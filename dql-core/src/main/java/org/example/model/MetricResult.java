package org.example.model;

import java.util.List;
import java.util.Map;

public record MetricResult(
        long slowCount,
        long nPlusOneCount,
        long totalQueries,
        List<TableFrequency> topTables,
        List<FingerprintFrequency> topFingerprints,
        Map<String, Long> queryTypeBreakdown
) {
}
