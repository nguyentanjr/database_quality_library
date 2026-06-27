    package org.example.model;

    import java.util.List;

    public record MetricResult(long slowCount, long nPLusOneCount, long totalQueries,
                               List<TableFrequency> topTables) {
    }
