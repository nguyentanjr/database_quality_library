package org.example.rules.config;

public record FullScanConfig(
        long minEstimatedRows,
        int maxExplainCandidates
) {

    public FullScanConfig {
        if (minEstimatedRows < 0) {
            throw new IllegalArgumentException("minEstimatedRows must be >= 0");
        }
        if (maxExplainCandidates <= 0) {
            throw new IllegalArgumentException("maxExplainCandidates must be > 0");
        }
    }

    public static FullScanConfig defaults() {
        return new FullScanConfig(10_000, 10);
    }
}
