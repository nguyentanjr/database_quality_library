package org.example.controller;

import org.example.config.DqlProperties;
import org.example.model.CollectedQuery;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.RequestTrace;
import org.example.model.RequestTraceEvent;
import org.example.model.RequestTraceQueryGroup;
import org.example.model.SchemaSnapshot;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/dql")
public class DqlTransparencyController {

    private static final int DEFAULT_QUERY_LIMIT = 50;
    private static final int MAX_QUERY_LIMIT = 500;
    private static final int DEFAULT_TRACE_LIMIT = 20;
    private static final int MAX_TRACE_LIMIT = 50;
    private static final int DEFAULT_TRACE_EVENT_LIMIT = 120;
    private static final int MAX_TRACE_EVENT_LIMIT = 500;
    private static final int DEFAULT_TRACE_GROUP_LIMIT = 40;
    private static final int MAX_TRACE_GROUP_LIMIT = 100;
    private static final Comparator<QueryExecution> EXECUTION_TIMESTAMP_ORDER =
            Comparator.comparing(QueryExecution::timestamp, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final ExecutionSink executionSink;
    private final SchemaSnapshotProvider schemaSnapshotProvider;
    private final DqlProperties properties;

    public DqlTransparencyController(
            ExecutionSink executionSink,
            SchemaSnapshotProvider schemaSnapshotProvider,
            DqlProperties properties
    ) {
        this.executionSink = executionSink;
        this.schemaSnapshotProvider = schemaSnapshotProvider;
        this.properties = properties;
    }

    @GetMapping("/collected-queries")
    public List<CollectedQuery> collectedQueries(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_QUERY_LIMIT) int limit
    ) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_QUERY_LIMIT));
        return executionSink.stats().stream()
                .map(CollectedQuery::from)
                .sorted(Comparator.comparingLong(CollectedQuery::count).reversed())
                .limit(effectiveLimit)
                .toList();
    }

    @GetMapping("/xray/traces")
    public List<RequestTrace> requestTraces(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_TRACE_LIMIT) int limit,
            @RequestParam(name = "eventLimit", defaultValue = "" + DEFAULT_TRACE_EVENT_LIMIT) int eventLimit,
            @RequestParam(name = "groupLimit", defaultValue = "" + DEFAULT_TRACE_GROUP_LIMIT) int groupLimit
    ) {
        int effectiveLimit = clamp(limit, 1, MAX_TRACE_LIMIT);
        int effectiveEventLimit = clamp(eventLimit, 1, MAX_TRACE_EVENT_LIMIT);
        int effectiveGroupLimit = clamp(groupLimit, 1, MAX_TRACE_GROUP_LIMIT);
        Map<String, TraceAccumulator> traces = new LinkedHashMap<>();

        List<QueryExecution> executions = executionSink.recentExecutions().stream()
                .filter(execution -> execution != null
                        && execution.contextId() != null
                        && !execution.contextId().isBlank())
                .sorted(EXECUTION_TIMESTAMP_ORDER)
                .toList();

        for (QueryExecution execution : executions) {
            traces.computeIfAbsent(
                    execution.contextId(),
                    ignored -> new TraceAccumulator(
                            execution.contextId(),
                            safeContextName(execution)
                    )
            ).add(execution);
        }

        return traces.values().stream()
                .map(trace -> toRequestTrace(trace, effectiveEventLimit, effectiveGroupLimit))
                .sorted(Comparator.comparingLong(DqlTransparencyController::endedEpochMillis).reversed())
                .limit(effectiveLimit)
                .toList();
    }

    @GetMapping("/schema-snapshot")
    public SchemaSnapshot schemaSnapshot() {
        try {
            return schemaSnapshotProvider.get();
        } catch (SQLException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL schema snapshot is not available",
                    e
            );
        }
    }

    private RequestTrace toRequestTrace(TraceAccumulator trace, int eventLimit, int groupLimit) {
        List<QueryExecution> executions = trace.executions.stream()
                .sorted(EXECUTION_TIMESTAMP_ORDER)
                .toList();
        Map<String, QueryGroupAccumulator> groups = new LinkedHashMap<>();
        long totalSqlMs = 0;
        int slowQueries = 0;
        int order = 0;

        for (QueryExecution execution : executions) {
            totalSqlMs += execution.durationMs();
            if (isSlow(execution)) {
                slowQueries++;
            }
            groups.computeIfAbsent(
                    fingerprintKey(execution),
                    QueryGroupAccumulator::new
            ).add(execution);
        }

        Map<String, List<String>> flagsByFingerprint = new HashMap<>();
        List<RequestTraceQueryGroup> allQueryGroups = groups.values().stream()
                .map(group -> toQueryGroup(group, flagsByFingerprint))
                .sorted(this::compareQueryGroups)
                .toList();
        List<RequestTraceQueryGroup> queryGroups = allQueryGroups.stream()
                .limit(groupLimit)
                .toList();

        List<RequestTraceEvent> events = new ArrayList<>();
        for (QueryExecution execution : executions) {
            String fingerprint = fingerprintKey(execution);
            List<String> flags = new ArrayList<>(
                    flagsByFingerprint.getOrDefault(fingerprint, List.of())
            );
            if (isSlow(execution) && !flags.contains("slow")) {
                flags.add("slow");
            }
            events.add(new RequestTraceEvent(
                    ++order,
                    execution.timestamp(),
                    execution.durationMs(),
                    fingerprint,
                    execution.sourceLocation(),
                    flags
            ));
        }

        int repeatedGroups = (int) allQueryGroups.stream()
                .filter(group -> group.count() > 1)
                .count();

        return new RequestTrace(
                trace.contextId,
                trace.contextName,
                executions.isEmpty() ? null : executions.getFirst().timestamp(),
                executions.isEmpty() ? null : executions.getLast().timestamp(),
                executions.size(),
                totalSqlMs,
                repeatedGroups,
                slowQueries,
                riskBadges(allQueryGroups, slowQueries),
                queryGroups,
                events.stream().limit(eventLimit).toList()
        );
    }

    private RequestTraceQueryGroup toQueryGroup(
            QueryGroupAccumulator group,
            Map<String, List<String>> flagsByFingerprint
    ) {
        List<String> flags = groupFlags(group);
        flagsByFingerprint.put(group.fingerprint, flags);
        return new RequestTraceQueryGroup(
                group.fingerprint,
                group.count,
                group.paramKeys.size(),
                group.count == 0 ? 0 : (double) group.totalMs / group.count,
                group.totalMs,
                topSourceLocation(group.sourceLocationCounts),
                flags
        );
    }

    private List<String> groupFlags(QueryGroupAccumulator group) {
        List<String> flags = new ArrayList<>();
        if (group.count > 1) {
            flags.add("repeated");
        }
        if (group.select
                && group.count >= properties.getNPlusOne().getMinCount()
                && group.paramKeys.size() >= properties.getNPlusOne().getMinDistinctParams()) {
            flags.add("n-plus-one-candidate");
        }
        if (group.maxMs >= properties.getSlowQueries().getThresholdMs()) {
            flags.add("slow");
        }
        return flags;
    }

    private List<String> riskBadges(List<RequestTraceQueryGroup> queryGroups, int slowQueries) {
        Set<String> badges = new LinkedHashSet<>();
        for (RequestTraceQueryGroup group : queryGroups) {
            if (group.flags().contains("n-plus-one-candidate")) {
                badges.add("N+1");
            } else if (group.flags().contains("repeated")) {
                badges.add("Repeated SQL");
            }
        }
        if (slowQueries > 0) {
            badges.add("Slow query");
        }
        return List.copyOf(badges);
    }

    private int compareQueryGroups(RequestTraceQueryGroup left, RequestTraceQueryGroup right) {
        int leftRisk = riskRank(left.flags());
        int rightRisk = riskRank(right.flags());
        if (leftRisk != rightRisk) {
            return Integer.compare(leftRisk, rightRisk);
        }
        int countCompare = Integer.compare(right.count(), left.count());
        if (countCompare != 0) {
            return countCompare;
        }
        return Long.compare(right.totalMs(), left.totalMs());
    }

    private int riskRank(List<String> flags) {
        if (flags.contains("n-plus-one-candidate")) {
            return 0;
        }
        if (flags.contains("slow")) {
            return 1;
        }
        if (flags.contains("repeated")) {
            return 2;
        }
        return 3;
    }

    private static String topSourceLocation(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean isSlow(QueryExecution execution) {
        return execution.durationMs() >= properties.getSlowQueries().getThresholdMs();
    }

    private static String safeContextName(QueryExecution execution) {
        if (execution.contextName() != null && !execution.contextName().isBlank()) {
            return execution.contextName();
        }
        return "Request " + execution.contextId().substring(0, Math.min(8, execution.contextId().length()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String fingerprintKey(QueryExecution execution) {
        String fingerprint = execution.fingerprint();
        return fingerprint == null || fingerprint.isBlank() ? "-" : fingerprint;
    }

    private static long endedEpochMillis(RequestTrace trace) {
        Instant endedAt = trace.endedAt();
        return endedAt == null ? 0 : endedAt.toEpochMilli();
    }

    private static class TraceAccumulator {
        private final String contextId;
        private final String contextName;
        private final List<QueryExecution> executions = new ArrayList<>();

        private TraceAccumulator(String contextId, String contextName) {
            this.contextId = contextId;
            this.contextName = contextName;
        }

        private void add(QueryExecution execution) {
            executions.add(execution);
        }
    }

    private static class QueryGroupAccumulator {
        private final String fingerprint;
        private int count;
        private long totalMs;
        private long maxMs;
        private boolean select;
        private final Set<String> paramKeys = new LinkedHashSet<>();
        private final Map<String, Integer> sourceLocationCounts = new HashMap<>();

        private QueryGroupAccumulator(String fingerprint) {
            this.fingerprint = fingerprint == null ? "-" : fingerprint;
        }

        private void add(QueryExecution execution) {
            count++;
            totalMs += execution.durationMs();
            maxMs = Math.max(maxMs, execution.durationMs());
            select = select || execution.select();
            collectParamKeys(execution);
            if (execution.sourceLocation() != null && !execution.sourceLocation().isBlank()) {
                sourceLocationCounts.merge(execution.sourceLocation(), 1, Integer::sum);
            }
        }

        private void collectParamKeys(QueryExecution execution) {
            if (execution.paramSets() == null) {
                return;
            }
            for (List<QueryParam> params : execution.paramSets()) {
                if (params == null || params.isEmpty()) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (QueryParam param : params) {
                    values.add(param == null ? "null" : String.valueOf(param.value()));
                }
                paramKeys.add(String.join("|", values));
            }
        }
    }
}
