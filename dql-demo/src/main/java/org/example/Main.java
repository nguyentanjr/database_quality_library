package org.example;

import org.example.adapter.PostgresExplainAdapter;
import org.example.analyzer.RuleEngine;
import org.example.collector.SchemaCollector;
import org.example.explain.ExplainAdapter;
import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.report.JsonReporter;
import org.example.rules.AnalysisContext;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.*;
import org.example.storage.ExecutionSink;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class Main implements CommandLineRunner {
    private final DataSource dataSource;
    private final ExecutionSink executionSink;
    private final ExplainAdapter explainAdapter;

    public Main(DataSource dataSource, ExecutionSink executionSink, ExplainAdapter explainAdapter) {
        this.dataSource = dataSource;
        this.executionSink = executionSink;
        this.explainAdapter = explainAdapter;
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        for (int i = 0; i < 10; i++) {
            runRealQueryWithId("""
        SELECT *
        FROM orders
        WHERE customer_id = ?
    """, 123 + i);
        }
        runRealQueryWithId("SELECT * FROM accounts WHERE id = ?", 1);
        runRealQueryWithId("SELECT * FROM accounts WHERE id = ?", 2);
        runRealQueryWithId("SELECT * FROM accounts WHERE id = ?", 999);
        runRealQueryWithAmount("""
    SELECT * FROM transactions t
    JOIN accounts a ON t.account_id = a.id
    WHERE t.amount > ?
""", 5);
        runRealQueryWithId("SELECT * FROM transactions WHERE id = ?", 1);
//        runBatchInsertAccounts();
//        runRealQueryWithAmount("""
//    SELECT * FROM transactions t
//    JOIN accounts a ON t.account_id = a.id
//    WHERE t.amount > ?
//""", 5);
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
                new R08_UnusedIndexRule(),
                new R09_NPlusOneRule(NPlusOneConfig.defaults()),
                new R10_FullScanTableRule(explainAdapter, FullScanConfig.defaults())
        ));

        executionSink.stats().forEach(System.out::println);

        List<Finding> findings = engine.run(ctx);
        findings.forEach(System.out::println);


        JsonReporter reporter = new JsonReporter();
        System.out.println(reporter.report(findings, snapshot.tables().size()));

    }

    @Scheduled(fixedDelay = 2000)
    public void scheduling() {
        try {
            SchemaSnapshot snapshot = new SchemaCollector().collect(dataSource);

            AnalysisContext ctx = new AnalysisContext(
                    snapshot,
                    executionSink.stats(),
                    executionSink.recentExecutions()
            );

            ExplainAdapter explainAdapter = new PostgresExplainAdapter(dataSource);
          //  explainAdapter.explain(ctx);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void runRealQueryWithId(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    System.out.println("first_col=" + resultSet.getObject(1));
                }
            }
        }
    }
    private void runRealQueryWithAmount(String sql, double amount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDouble(1, amount);

            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    System.out.println("first_col=" + resultSet.getObject(1));
                }
            }
        }
    }

    private void runBatchInsertAccounts() throws Exception {
        String sql = """
        INSERT INTO accounts(id, email, balance)
        VALUES (?, ?, ?)
        ON CONFLICT (id) DO NOTHING
    """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, 101L);
            ps.setString(2, "user101@test.com");
            ps.setBigDecimal(3, new java.math.BigDecimal("1000.00"));
            ps.addBatch();

            ps.setLong(1, 102L);
            ps.setString(2, "user102@test.com");
            ps.setBigDecimal(3, new java.math.BigDecimal("2000.00"));
            ps.addBatch();

            ps.setLong(1, 103L);
            ps.setString(2, "user103@test.com");
            ps.setBigDecimal(3, new java.math.BigDecimal("3000.00"));
            ps.addBatch();

            ps.executeBatch();
        }
    }
}
