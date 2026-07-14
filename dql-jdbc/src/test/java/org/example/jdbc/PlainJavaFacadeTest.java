package org.example.jdbc;

import io.demo.plain.DemoWorkload;
import org.example.model.Finding;
import org.example.model.QualityReport;
import org.example.model.QueryStats;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bằng chứng chạy được cho luận điểm "core framework-independent":
 * toàn bộ pipeline (wrap -> capture -> fingerprint -> sink -> rules ->
 * report -> sourceLocation) chạy trên H2 + JDK thuần, KHÔNG có Spring
 * trên classpath của module này.
 */
class PlainJavaFacadeTest {

    private static DataSource h2;
    private static DqlRuntime dql;
    private static QualityReport report;

    @BeforeAll
    static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:dqlplain;DB_CLOSE_DELAY=-1");
        h2 = ds;

        DemoWorkload.createFixtures(h2);

        dql = Dql.attach(h2);
        DataSource monitored = dql.dataSource();

        DemoWorkload.selectStar(monitored);
        DemoWorkload.nPlusOne(monitored, dql, 6);          // 6 lần, 6 param distinct -> fire
        DemoWorkload.repeatedSameParam(monitored, dql, 6); // 6 lần, 1 param -> KHÔNG fire

        report = dql.analyzeNow();
    }

    @AfterAll
    static void tearDown() {
        if (dql != null) {
            dql.close();
        }
    }

    @Test
    void schema_rules_fire_on_h2_fixtures() {
        Set<String> ruleIds = ruleIds();
        assertTrue(ruleIds.contains("R01_MissingPrimaryKey"), "plain_no_pk phải fire R01: " + ruleIds);
        assertTrue(ruleIds.contains("R02_SuspiciousType"), "amount REAL phải fire R02: " + ruleIds);
        assertTrue(ruleIds.contains("R03_NullableRisk"), "owner_id FK nullable phải fire R03: " + ruleIds);
    }

    @Test
    void select_star_fires_r04() {
        assertTrue(ruleIds().contains("R04_SelectStar"), ruleIds().toString());
    }

    @Test
    void n_plus_one_fires_with_source_location_pointing_to_demo_code() {
        List<Finding> r09 = report.findings().stream()
                .filter(f -> "R09_NPlusOne".equals(f.ruleId()))
                .toList();
        assertEquals(1, r09.size(),
                "chỉ fingerprint 6-param-distinct được fire (negative case cùng param KHÔNG fire): " + r09);

        Object sourceLocation = r09.get(0).details().get("sourceLocation");
        assertNotNull(sourceLocation, "finding R09 phải kèm sourceLocation");
        assertTrue(sourceLocation.toString().contains("io.demo.plain.DemoWorkload"),
                "sourceLocation phải trỏ code nghiệp vụ (io.demo.*), không phải facade/driver: " + sourceLocation);
    }

    @Test
    void collected_queries_expose_stats_with_source_location_and_masking() {
        List<QueryStats> collected = dql.collectedQueries();
        assertFalse(collected.isEmpty());

        QueryStats nPlusOneShape = collected.stream()
                .filter(s -> s.fingerprint().contains("plain_account"))
                .findFirst()
                .orElseThrow();

        assertEquals(12, nPlusOneShape.count()); // 6 distinct + 6 duplicate
        assertTrue(nPlusOneShape.fingerprint().contains("?"), "fingerprint phải mask literal");
        assertFalse(nPlusOneShape.fingerprint().matches(".*\\b[0-9]+\\b.*"),
                "không được lộ giá trị param: " + nPlusOneShape.fingerprint());
        assertNotNull(nPlusOneShape.topSourceLocation());
        assertTrue(nPlusOneShape.topSourceLocation().contains("DemoWorkload"), nPlusOneShape.topSourceLocation());
        assertTrue(nPlusOneShape.maxMs() >= nPlusOneShape.minMs());
        assertNotNull(nPlusOneShape.lastSeen());
    }

    @Test
    void report_and_ai_context_are_cached_like_starter_scheduler() {
        assertNotNull(dql.latestReport());
        assertNotNull(dql.latestAiContext());
        assertNotNull(dql.lastRun());
        assertTrue(report.score() < 100, "fixture đầy lỗi thì score phải bị trừ");
        assertEquals(3, dql.schemaSnapshot().tables().size());
    }

    private Set<String> ruleIds() {
        return report.findings().stream()
                .map(Finding::ruleId)
                .collect(Collectors.toSet());
    }
}
