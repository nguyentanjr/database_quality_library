package org.example.controller;

import org.example.model.MetricResult;
import org.example.model.QueryStats;
import org.example.model.TableFrequency;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R09_NPlusOneRule;
import org.example.storage.ExecutionSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dql")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "dql.endpoints",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MetricController {

    private final ExecutionSink executionSink;

    private final R09_NPlusOneRule r09NPlusOneRule;

    public MetricController(ExecutionSink executionSink, R09_NPlusOneRule r09NPlusOneRule) {
        this.executionSink = executionSink;
        this.r09NPlusOneRule = r09NPlusOneRule;
    }


    @GetMapping("/metrics")
    public MetricResult getMetricResult() {

        long slowCount = executionSink.slowQueryCount();
        long totalQueries = executionSink.totalQuery();
        AnalysisContext context = new AnalysisContext(null, null, executionSink.recentExecutions());
        long nPlusOneCount = r09NPlusOneRule.analyze(context).size();
        return new MetricResult(slowCount, nPlusOneCount, totalQueries, topTables(executionSink.stats()));
    }


    public List<TableFrequency> topTables(List<QueryStats> stats) {
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