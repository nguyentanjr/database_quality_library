package org.example.model;

import java.util.List;

public record   AiContext(
        int tableCount,
        List<String> schemaSummary,
        List<Finding> topFindings,
        List<TableFrequency> topTables,
        List<String> sampleQueries
) {
}
