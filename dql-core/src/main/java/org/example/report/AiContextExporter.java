package org.example.report;

import org.example.model.AiContext;
import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.ForeignKey;
import org.example.model.Index;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.model.TableFrequency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiContextExporter {

    private final int TOP_FINDINGS;
    private final int SAMPLE_QUERIES;
    private final int TOP_TABLES;

    public AiContextExporter() {
        this(5, 5, 5);
    }

    public AiContextExporter(int topFindings, int sampleQueries, int topTables) {
        this.TOP_FINDINGS = topFindings;
        this.SAMPLE_QUERIES = sampleQueries;
        this.TOP_TABLES = topTables;
    }

    public AiContext export(
            SchemaSnapshot snapshot,
            List<Finding> findings,
            List<QueryStats> stats,
            List<QueryExecution> recentExecutions
    ) {
        List<String> schemaSummary = buildSchemaSummary(snapshot);
        List<Finding> importantFindings = FindingPriority.topEnsuringSevere(findings, TOP_FINDINGS);
        List<TableFrequency> mostUsedTables = findMostUsedTables(stats);
        List<String> querySamples = pickQuerySamples(recentExecutions);

        return new AiContext(
                snapshot.tables().size(),
                schemaSummary,
                importantFindings,
                mostUsedTables,
                querySamples
        );
    }

    private List<String> buildSchemaSummary(SchemaSnapshot snapshot) {
        List<String> result = new ArrayList<>();

        for (Table table : snapshot.tables()) {
            result.add(describeTable(table));
        }

        return result;
    }

    private String describeTable(Table table) {
        return table.name()
                + " | pk=" + table.primaryKey()
                + " | cols=[" + describeColumns(table.columns()) + "]"
                + " | fks=[" + describeForeignKeys(table.foreignKeys()) + "]"
                + " | indexes=[" + describeIndexes(table.indexes()) + "]";
    }

    private String describeColumns(List<Column> columns) {
        List<String> parts = new ArrayList<>();

        for (Column column : columns) {
            parts.add(describeColumn(column));
        }

        return String.join(", ", parts);
    }

    private String describeColumn(Column column) {
        String result = column.name() + ":" + column.type();

        if (column.nullable()) {
            result += " null";
        }

        return result;
    }

    private String describeForeignKeys(List<ForeignKey> foreignKeys) {
        List<String> parts = new ArrayList<>();

        for (ForeignKey fk : foreignKeys) {
            parts.add(fk.column() + "->" + fk.refTable() + "." + fk.refColumn());
        }

        return String.join(", ", parts);
    }

    private String describeIndexes(List<Index> indexes) {
        List<String> parts = new ArrayList<>();

        for (Index index : indexes) {
            parts.add(describeIndex(index));
        }

        return String.join(", ", parts);
    }

    private String describeIndex(Index index) {
        String result = index.indexName()
                + "(" + String.join(",", index.indexColumns()) + ")";

        if (index.unique()) {
            result += " unique";
        }

        return result;
    }

    private List<TableFrequency> findMostUsedTables(List<QueryStats> stats) {
        Map<String, Long> tableCounts = countTableUsage(stats);
        List<TableFrequency> result = new ArrayList<>();

        List<Map.Entry<String, Long>> entries = new ArrayList<>(tableCounts.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());

        for (Map.Entry<String, Long> entry : entries) {
            if (result.size() >= TOP_TABLES) {
                break;
            }

            result.add(new TableFrequency(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    private Map<String, Long> countTableUsage(List<QueryStats> stats) {
        Map<String, Long> counts = new HashMap<>();

        for (QueryStats stat : stats) {
            long queryCount = stat.count();

            for (String table : stat.tables()) {
                long currentCount = counts.getOrDefault(table, 0L);
                counts.put(table, currentCount + queryCount);
            }
        }

        return counts;
    }

    private List<String> pickQuerySamples(List<QueryExecution> recentExecutions) {
        Set<String> distinctFingerprints = new LinkedHashSet<>();

        for (QueryExecution execution : recentExecutions) {
            if (distinctFingerprints.size() >= SAMPLE_QUERIES) {
                break;
            }

            distinctFingerprints.add(execution.fingerprint());
        }

        return new ArrayList<>(distinctFingerprints);
    }
}