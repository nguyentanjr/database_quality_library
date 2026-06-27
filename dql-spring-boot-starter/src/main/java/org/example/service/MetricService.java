package org.example.service;

import org.example.model.QueryStats;
import org.example.model.TableFrequency;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricService {

    private List<TableFrequency> topTables(List<QueryStats> stats) {
        Map<String, Long> tableCounts = new HashMap<>();

        for (QueryStats stat : stats) {
            for (String table : stat.tables()) {
                tableCounts.merge(table, stat.count(), Long::sum);
            }
        }

        return tableCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new TableFrequency(e.getKey(), e.getValue()))
                .toList();
    }
}
