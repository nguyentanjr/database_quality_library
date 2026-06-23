package org.example.controller;

import org.example.analyzer.RuleEngine;
import org.example.collector.SchemaCollector;
import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.storage.ExecutionSink;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dql")
public class DqlReportController {

    private final DataSource dataSource;
    private final ExecutionSink executionSink;
    private final List<Rule> rules;

    public DqlReportController(
            DataSource dataSource,
            ExecutionSink executionSink,
            List<Rule> rules
    ) {
        this.dataSource = dataSource;
        this.executionSink = executionSink;
        this.rules = rules;
    }

    @GetMapping("/findings")
    public List<Finding> findings() throws SQLException {
        Map<String, Boolean> groupByFingerprint = new HashMap<>();
        SchemaSnapshot snapshot = new SchemaCollector().collect(dataSource);

        AnalysisContext ctx = new AnalysisContext(
                snapshot,
                executionSink.stats(),
                executionSink.recentExecutions()
        );

        RuleEngine engine = new RuleEngine(rules);
        List<Finding> findings = engine.run(ctx);
        System.out.println(findings);
        return List.of();
    }


}