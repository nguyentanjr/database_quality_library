package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.*;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class R08_UnusedIndexRule implements Rule {

    public static final String ID = "R08_UnusedIndex";

    public static final Severity SEVERITY = Severity.LOW;

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();

        SchemaSnapshot snapshot = context.snapshot();
        List<QueryStats> queryStats = context.queryStats();

        for (Table table : snapshot.tables()) {
            Set<String> usedColumns = usedColumnsOfTable(table.name(), queryStats);
            for (Index index : table.indexes()) {
                List<String> indexColumns = index.indexColumns();

                if (indexColumns == null || indexColumns.isEmpty()) {
                    continue;
                }

                String leadingColumn = indexColumns.getFirst();

                if (table.primaryKey().contains(leadingColumn) || index.unique()) {
                    continue;
                }

                if (usedColumns.contains(leadingColumn)) {
                    continue;
                }

                // indexName để phân biệt nhiều index unused trên cùng bảng/leading column.
                // Map.of ném NPE nếu value null -> guard (SchemaCollector đảm bảo non-null nhưng thủ contract).
                Map<String, Object> details = index.indexName() == null
                        ? Map.of()
                        : Map.of("indexName", index.indexName());

                findings.add(new Finding(
                        ID,
                        SEVERITY,
                        "Index appears unused by observed queries",
                        "Review this index. If it is not required for constraints or important queries, consider removing it",
                        table.name(),
                        leadingColumn,
                        null,
                        details
                ));
            }
        }

        return findings;
    }

    private Set<String> usedColumnsOfTable(String tableName, List<QueryStats> queryStats) {
        Set<String> usedColumns = new HashSet<>();

        for (QueryStats stats : queryStats) {
            if (!stats.tables().contains(tableName)) {
                continue;
            }

            for (String column : stats.columns()) {
                if (column == null || column.isBlank()) {
                    continue;
                }

                usedColumns.add(column);
            }
        }

        return usedColumns;
    }
}