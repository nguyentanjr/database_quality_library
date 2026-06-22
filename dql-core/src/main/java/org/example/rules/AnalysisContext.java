package org.example.rules;

import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;

import java.util.List;

public record AnalysisContext(
        SchemaSnapshot snapshot,
        List<QueryStats> queryStats,
        List<QueryExecution> recentExecutions
) {}