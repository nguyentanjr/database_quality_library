import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.rules.AnalysisContext;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.impl.R09_NPlusOneRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R09_NPlusOneRuleTest {

    private static final String FP = "select * from pet where owner_id = ?";
    // minCount=3, minDistinctParams=2 (nhỏ hơn default cho fixture gọn)
    private final NPlusOneConfig config = new NPlusOneConfig(3, 2);

    private QueryExecution query(String contextId, Object paramValue, boolean select) {
        return new QueryExecution(
                "select * from pet where owner_id = " + paramValue,
                1,
                Instant.now(),
                contextId,
                FP,
                select,
                List.of(List.of(new QueryParam(1, paramValue))),
                null);
    }

    @Test
    void fires_when_same_context_repeats_with_distinct_params() {
        List<QueryExecution> execs = List.of(
                query("req-1", 10, true),
                query("req-1", 20, true),
                query("req-1", 30, true));
        AnalysisContext ctx = new AnalysisContext(null, List.of(), execs);

        List<Finding> findings = new R09_NPlusOneRule(config).analyze(ctx);

        assertEquals(1, findings.size());
        Finding f = findings.getFirst();
        assertEquals(R09_NPlusOneRule.ID, f.ruleId());
        assertEquals(FP, f.fingerprint());
        assertEquals(1, f.details().get("requestsAffected"));
        assertEquals(3, f.details().get("maxCount"));
        assertEquals(3, f.details().get("maxDistinctParams"));
    }

    @Test
    void does_not_fire_when_params_repeat_same_value() {
        // cache-miss pattern: lặp cùng 1 param -> distinctParams=1 < minDistinctParams
        List<QueryExecution> execs = List.of(
                query("req-1", 10, true),
                query("req-1", 10, true),
                query("req-1", 10, true));
        AnalysisContext ctx = new AnalysisContext(null, List.of(), execs);

        assertTrue(new R09_NPlusOneRule(config).analyze(ctx).isEmpty());
    }

    @Test
    void does_not_merge_across_different_contexts() {
        // 2 context, mỗi cái chỉ 2 lần -> per-context count=2 < 3. Nếu gộp sai theo
        // fingerprint (bỏ context) thì count=4 sẽ fire. assertEmpty chứng minh gom per-context.
        List<QueryExecution> execs = List.of(
                query("req-1", 10, true),
                query("req-1", 20, true),
                query("req-2", 30, true),
                query("req-2", 40, true));
        AnalysisContext ctx = new AnalysisContext(null, List.of(), execs);

        assertTrue(new R09_NPlusOneRule(config).analyze(ctx).isEmpty());
    }

    @Test
    void does_not_fire_for_write_statements() {
        // select=false (INSERT/UPDATE lặp) -> bị lọc, không phải N+1
        List<QueryExecution> execs = List.of(
                query("req-1", 10, false),
                query("req-1", 20, false),
                query("req-1", 30, false));
        AnalysisContext ctx = new AnalysisContext(null, List.of(), execs);

        assertTrue(new R09_NPlusOneRule(config).analyze(ctx).isEmpty());
    }
}
