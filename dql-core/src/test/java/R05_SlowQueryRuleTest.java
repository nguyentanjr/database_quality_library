import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.rules.AnalysisContext;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.R05_SlowQueryRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R05_SlowQueryRuleTest {

    private static final String FP = "select * from orders where status = ?";

    private QueryExecution exec(long durationMs) {
        return new QueryExecution(
                "select * from orders where status = 'A'",
                durationMs,
                Instant.now(),
                "ctx",
                FP,
                true,
                List.of(),
                null);
    }

    @Test
    void fires_when_duration_exceeds_threshold() {
        AnalysisContext ctx = new AnalysisContext(null, List.of(),
                List.of(exec(500), exec(1200)));

        List<Finding> findings = new R05_SlowQueryRule(new SlowQueryConfig(100)).analyze(ctx);

        assertEquals(1, findings.size(), "gom per-fingerprint -> 1 finding");
        Finding finding = findings.getFirst();
        assertEquals(R05_SlowQueryRule.ID, finding.ruleId());
        assertEquals(FP, finding.fingerprint());
        assertEquals(2, finding.details().get("slowCount"));
        assertEquals(1200L, finding.details().get("maxDurationMs"));
    }

    @Test
    void does_not_fire_when_below_threshold() {
        AnalysisContext ctx = new AnalysisContext(null, List.of(),
                List.of(exec(50), exec(80)));

        assertTrue(new R05_SlowQueryRule(new SlowQueryConfig(100)).analyze(ctx).isEmpty());
    }

    @Test
    void fires_at_exactly_threshold() {
        // Thống nhất với sink (đếm slow bằng >= threshold): tại ĐÚNG ngưỡng cũng là slow.
        AnalysisContext ctx = new AnalysisContext(null, List.of(), List.of(exec(100)));

        assertEquals(1, new R05_SlowQueryRule(new SlowQueryConfig(100)).analyze(ctx).size());
    }
}
