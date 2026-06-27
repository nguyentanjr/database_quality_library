package org.example.controller;

import org.example.model.MetricResult;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R09_NPlusOneRule;
import org.example.storage.ExecutionSink;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dql")
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

        return new MetricResult(slowCount, nPlusOneCount, totalQueries);
    }
}
