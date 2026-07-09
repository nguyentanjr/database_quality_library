package org.example.controller;

import org.example.fingerprint.SqlVerbs;
import org.example.model.FingerprintFrequency;
import org.example.model.MetricResult;
import org.example.model.QueryStats;
import org.example.model.TableFrequency;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R09_NPlusOneRule;
import org.example.storage.ExecutionSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/dql")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "dql.endpoints",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MetricController {

    private final ExecutionSink executionSink;

    private final R09_NPlusOneRule r09NPlusOneRule;

    public MetricController(ExecutionSink executionSink, R09_NPlusOneRule r09NPlusOneRule) {
        this.executionSink = executionSink;
        this.r09NPlusOneRule = r09NPlusOneRule;
    }


    @GetMapping("/metrics")
    public MetricResult getMetricResult() {

        long slowCount = executionSink.slowQueryCount();
        long totalQueries = executionSink.totalQuery();
        AnalysisContext context = new AnalysisContext(null, null, executionSink.recentExecutions());
        long nPlusOneCount = r09NPlusOneRule.analyze(context).size();

        List<QueryStats> stats = executionSink.stats();
        return new MetricResult(
                slowCount,
                nPlusOneCount,
                totalQueries,
                topTables(stats),
                topFingerprints(stats),
                queryTypeBreakdown(stats)
        );
    }

    public List<FingerprintFrequency> topFingerprints(List<QueryStats> stats) {
        return stats.stream()
                .sorted(Comparator.comparingLong(QueryStats::count).reversed())
                .limit(5)
                .map(s -> new FingerprintFrequency(s.fingerprint(), s.count()))
                .toList();
    }

    public Map<String, Long> queryTypeBreakdown(List<QueryStats> stats) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (String verb : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
            breakdown.put(verb, 0L);
        }
        for (QueryStats stat : stats) {
            breakdown.merge(verbOf(stat.fingerprint()), stat.count(), Long::sum);
        }
        return breakdown;
    }

    private String verbOf(String fingerprint) {
        // Dùng chung SqlVerbs với BPP để CTE ("with ... select") đếm vào SELECT thay vì OTHER.
        String verb = SqlVerbs.mainVerb(fingerprint);
        return verb == null ? "OTHER" : verb.toUpperCase(Locale.ROOT);
    }


    public List<TableFrequency> topTables(List<QueryStats> stats) {
        Map<String, Long> tableCounts = new HashMap<>();

        for (QueryStats stat : stats) {
            for (String table : stat.tables()) {
                tableCounts.merge(table, stat.count(), Long::sum);
            }
        }

        return tableCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new TableFrequency(e.getKey(), e.getValue()))
                .toList();
    }
}