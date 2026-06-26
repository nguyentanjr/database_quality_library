package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.explain.ExplainAdapter;
import org.example.model.ExplainResult;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class R10_FullScanTableRule implements Rule {
    public static final String ID = "R10_FullScanTable";

    public static final Severity SEVERITY = Severity.HIGH;

    private static final Logger LOGGER = Logger.getLogger(R10_FullScanTableRule.class.getName());

    private final ExplainAdapter explainAdapter;
    private final FullScanConfig config;

    public R10_FullScanTableRule(
            ExplainAdapter explainAdapter,
            FullScanConfig config
    ) {
        this.explainAdapter = Objects.requireNonNull(explainAdapter, "explainAdapter");
        this.config = Objects.requireNonNullElse(config, FullScanConfig.defaults());
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        List<QueryExecution> candidates = selectCandidates(context);

        for (QueryExecution execution : candidates) {
            try {
                Optional<ExplainResult> result = explainAdapter.explain(execution);

                if (result.isEmpty()) {
                    continue;
                }

                ExplainResult explain = result.get();

                if (explain.fullScan()
                        && explain.estimatedRows() >= config.minEstimatedRows()) {
                    findings.add(toFinding(execution, explain));
                }
            } catch (Exception e) {
                LOGGER.fine(() -> "Skip R10 full scan explain failure for fingerprint: " + execution.fingerprint());
            }
        }

        return findings;
    }

    private List<QueryExecution> selectCandidates(AnalysisContext context) {
        List<QueryStats> queryStats = context.queryStats() == null
                ? List.of()
                : context.queryStats();
        List<QueryExecution> recentExecutions = context.recentExecutions() == null
                ? List.of()
                : context.recentExecutions();

        Map<String, QueryExecution> sampleByFingerprint = new HashMap<>();
        for (QueryExecution execution : recentExecutions) {
            if (execution == null || execution.fingerprint() == null) {
                continue;
            }

            sampleByFingerprint.putIfAbsent(execution.fingerprint(), execution);
        }

        List<QueryExecution> candidates = new ArrayList<>();

        queryStats.stream()
                .filter(Objects::nonNull)
                .filter(QueryStats::select)
                .filter(stats -> stats.columns() != null && !stats.columns().isEmpty())
                .sorted(Comparator.comparingLong(QueryStats::count).reversed())
                .limit(config.maxExplainCandidates())
                .forEach(stats -> addSampleCandidate(stats, sampleByFingerprint, candidates));

        return candidates;
    }

    private void addSampleCandidate(
            QueryStats stats,
            Map<String, QueryExecution> sampleByFingerprint,
            List<QueryExecution> candidates
    ) {
        QueryExecution sample = sampleByFingerprint.get(stats.fingerprint());
        if (sample == null) {
            LOGGER.fine(() -> "Skip R10 full scan explain because recent execution sample is missing for fingerprint: "
                    + stats.fingerprint());
            return;
        }

        candidates.add(sample);
    }

    private Finding toFinding(QueryExecution execution, ExplainResult explain) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("estimatedRows", explain.estimatedRows());
        details.put("estimatedCost", explain.estimatedCost());
        details.put("nodeType", explain.reason());
        details.put("sql", execution.sqlRaw());

        return new Finding(
                ID,
                SEVERITY,
                "Query performs a large table full scan",
                "Add a selective index or narrow the query predicate",
                explain.tableName(),
                null,
                execution.fingerprint(),
                details
        );
    }
}
