package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.SlowQueryConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class R05_SlowQueryRule implements Rule {

    public static final String ID = "R05_SlowQuery";

    public static final Severity SEVERITY = Severity.HIGH;

    private final SlowQueryConfig config;

    public R05_SlowQueryRule(SlowQueryConfig config) {
        this.config = config;
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        List<QueryExecution> queryExecutions = context.recentExecutions();
        Map<String, SlowQueryAccumulator> slowQueries = new HashMap<>();

        for(QueryExecution queryExecution : queryExecutions) {
            long duration = queryExecution.durationMs();
            if(duration > config.thresholdMs()) {
                SlowQueryAccumulator acc = slowQueries.computeIfAbsent(queryExecution.fingerprint(),
                        k -> new SlowQueryAccumulator(
                                queryExecution.fingerprint()
                                ));
                acc.record(duration);
            }
        }
        long threshold = config.thresholdMs();
        for (SlowQueryAccumulator acc : slowQueries.values()) {

            findings.add(new Finding(
                    ID,
                    SEVERITY,
                    "Slow query",
                    "Query exceeded " + threshold + "ms",
                    null,
                    null,
                    acc.getFingerprint(),
                    Map.of(
                            "thresholdMs", threshold,
                            "slowCount", acc.getSlowCount(),
                            "maxDurationMs", acc.getMaxDurationMs()
                    )));
        }
        return findings;

    }

    private static class  SlowQueryAccumulator {
        private final String fingerprint;
        private int slowCount;
        private long maxDurationMs;

        public SlowQueryAccumulator( String fingerprint) {
            this.fingerprint = fingerprint;
        }

         public void record(long durationMs) {
            slowCount++;
            maxDurationMs = Math.max(maxDurationMs, durationMs);
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public int getSlowCount() {
            return slowCount;
        }

        public long getMaxDurationMs() {
            return maxDurationMs;
        }

    }
}
