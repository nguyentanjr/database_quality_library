import org.example.analyzer.RuleEngine;
import org.example.collector.SchemaCollector;
import org.example.model.Finding;
import org.example.model.QualityReport;
import org.example.model.SchemaSnapshot;
import org.example.report.JsonReporter;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R01_MissingPrimaryKeyRule;
import org.example.rules.impl.R02_SuspiciousTypeRule;
import org.example.rules.impl.R03_NullableRiskRule;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RuleEngineIT {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
            .withInitScript("init.sql");

    private DataSource ds() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(pg.getJdbcUrl());
        ds.setUser(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return ds;
    }

    @Test
    void runs_schema_rules_and_builds_report() throws Exception {
        SchemaSnapshot snap = new SchemaCollector().collect(ds());

        AnalysisContext ctx = new AnalysisContext(snap, List.of(), List.of());

        RuleEngine engine = new RuleEngine(List.of(
                new R01_MissingPrimaryKeyRule(),
                new R02_SuspiciousTypeRule(),
                new R03_NullableRiskRule()
        ));

        List<Finding> findings = engine.run(ctx);

        assertTrue(hasRule(findings, R01_MissingPrimaryKeyRule.ID));
        assertTrue(hasRule(findings, R02_SuspiciousTypeRule.ID));
        assertTrue(hasRule(findings, R03_NullableRiskRule.ID));

        assertFalse(
                findings.stream()
                        .anyMatch(f -> "balance".equals(f.column()))
        );

        JsonReporter reporter = new JsonReporter();

        QualityReport report = reporter.buildReport(
                findings,
                snap.tables().size()
        );

        int expectedScore = Math.max(0, 100 - findings.stream()
                .mapToInt(f -> f.severity().getScore())
                .sum());
        assertEquals(expectedScore, report.score());

        assertTrue(findings.stream().anyMatch(f ->
                f.ruleId().equals(R03_NullableRiskRule.ID) && "account_id".equals(f.column())));
        assertEquals(4, findings.size());
    }


    private boolean hasRule(List<Finding> findings, String ruleId) {
        return findings.stream()
                .anyMatch(f -> f.ruleId().equals(ruleId));
    }
}
