package org.example.model;

public record ExplainResult(
        boolean fullScan,
        String tableName,
        long estimatedRows,
        double estimatedCost,
        String reason
) {}