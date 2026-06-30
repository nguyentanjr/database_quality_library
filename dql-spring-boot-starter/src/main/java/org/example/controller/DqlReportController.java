package org.example.controller;

import org.example.analyzer.RuleEngine;
import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.rules.AnalysisContext;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/dql")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "dql.endpoints",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DqlReportController {

    private final ExecutionSink executionSink;
    private final RuleEngine ruleEngine;
    private final SchemaSnapshotProvider schemaProvider;

    public DqlReportController(
            ExecutionSink executionSink,
            RuleEngine ruleEngine, SchemaSnapshotProvider schemaProvider
    ) {
        this.executionSink = executionSink;
        this.ruleEngine = ruleEngine;
        this.schemaProvider = schemaProvider;
    }

    @GetMapping("/findings")
    public List<Finding> findings() throws SQLException {
        SchemaSnapshot snapshot = schemaProvider.get();

        AnalysisContext ctx = new AnalysisContext(
                snapshot,
                executionSink.stats(),
                executionSink.recentExecutions()
        );

        return ruleEngine.run(ctx);
    }
}
