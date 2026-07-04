import org.example.analyzer.RuleEngine;
import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QualityReport;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.report.JsonReporter;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.ScoreConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.R05_SlowQueryRule;
import org.example.rules.impl.R09_NPlusOneRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-09: cùng dataset, đổi config -> finding/score đổi theo. */
class ConfigDrivenTest {

    private QueryExecution slowExec(long durationMs) {
        return new QueryExecution("select 1", durationMs, Instant.now(), "ctx",
                "select ?", true, List.of(), null);
    }

    @Test
    void slow_query_threshold_changes_outcome() {
        AnalysisContext ctx = new AnalysisContext(null, List.of(),
                List.of(slowExec(200)));

        assertTrue(new R05_SlowQueryRule(new SlowQueryConfig(1000)).analyze(ctx).isEmpty(),
                "threshold 1000ms -> 200ms không phải slow");
        assertEquals(1, new R05_SlowQueryRule(new SlowQueryConfig(100)).analyze(ctx).size(),
                "threshold 100ms -> 200ms là slow");
    }

    @Test
    void n_plus_one_threshold_changes_outcome() {
        QueryExecution[] execs = {
                nPlusOne(10), nPlusOne(20), nPlusOne(30)
        };
        AnalysisContext ctx = new AnalysisContext(null, List.of(), List.of(execs));

        assertTrue(new R09_NPlusOneRule(new NPlusOneConfig(5, 3)).analyze(ctx).isEmpty(),
                "minCount 5 -> 3 lần chưa đủ");
        assertEquals(1, new R09_NPlusOneRule(new NPlusOneConfig(3, 3)).analyze(ctx).size(),
                "minCount 3 -> đủ");
    }

    private QueryExecution nPlusOne(Object param) {
        return new QueryExecution("select * from pet where owner_id = " + param, 1,
                Instant.now(), "req-1", "select * from pet where owner_id = ?", true,
                List.of(List.of(new QueryParam(1, param))), null);
    }

    @Test
    void score_weights_change_report_score() {
        List<Finding> findings = List.of(
                new Finding("R", Severity.CRITICAL, "m", "s", null, null, null, Map.of()));

        int defaultScore = new JsonReporter().buildReport(findings, 1).score();
        assertEquals(85, defaultScore, "default CRITICAL weight = 15 -> 100-15");

        Map<Severity, Integer> heavy = new EnumMap<>(Severity.class);
        heavy.put(Severity.CRITICAL, 50);
        int heavyScore = new JsonReporter(new ScoreConfig(heavy)).buildReport(findings, 1).score();
        assertEquals(50, heavyScore, "CRITICAL weight tune 50 -> 100-50");
    }

    @Test
    void rule_toggle_is_list_driven_and_id_matches_key() {

        AnalysisContext ctx = new AnalysisContext(null, List.of(), List.of(slowExec(200)));
        Rule r05 = new R05_SlowQueryRule(new SlowQueryConfig(100));

        assertEquals("R05_SlowQuery", r05.id(), "id() = khóa dùng cho dql.rules.<id>");
        assertEquals(1, new RuleEngine(List.of(r05)).run(ctx).size(), "rule có trong list -> chạy");
        assertTrue(new RuleEngine(List.of()).run(ctx).isEmpty(), "rule bị lọc khỏi list -> im");
    }
}
