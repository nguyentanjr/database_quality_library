package org.example;

import org.example.analyzer.RuleEngine;
import org.example.collector.SchemaCollector;
import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.report.JsonReporter;
import org.example.rules.AnalysisContext;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.*;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
public class Main implements CommandLineRunner {
    private final DataSource dataSource;
    private final ExecutionSink executionSink;

    public Main(DataSource dataSource, ExecutionSink executionSink) {
        this.dataSource = dataSource;
        this.executionSink = executionSink;
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        runRealQuery("SELECT * FROM accounts WHERE id = 1");
        runRealQuery("SELECT * FROM accounts WHERE id = 2");
        runRealQuery("SELECT * FROM accounts WHERE id = 999");
        runRealQuery("SELECT * FROM transactions WHERE id = 1");
        runRealQuery("""
    SELECT * FROM transactions t
    JOIN accounts a ON t.account_id = a.id
    WHERE t.amount > 5
""");
        SchemaSnapshot snapshot = new SchemaCollector().collect(dataSource);
        AnalysisContext ctx = new AnalysisContext(snapshot, executionSink.stats(), executionSink.recentExecutions());
        RuleEngine engine = new RuleEngine(List.of(
                new R01_MissingPrimaryKeyRule(),
                new R02_SuspiciousTypeRule(),
                new R03_NullableRiskRule(),
                new R04_SelectStarRule(),
                new R05_SlowQueryRule(SlowQueryConfig.defaults()),
                new R06_UnindexedFKRule(),
                new R07_MissingIndexRule(MissingIndexConfig.defaults()),
                new R08_UnusedIndexRule()
        ));

        executionSink.stats().forEach(System.out::println);

        List<Finding> findings = engine.run(ctx);
        findings.forEach(System.out::println);

        JsonReporter reporter = new JsonReporter();
        System.out.println(reporter.report(findings, snapshot.tables().size()));
    }

    private void runRealQuery(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                System.out.println("accounts_count=" + resultSet.getLong(1));
            }
        }
    }
}
