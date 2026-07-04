import org.example.explain.ExplainAdapter;
import org.example.model.ExplainResult;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;
import org.example.rules.config.FullScanConfig;
import org.example.rules.impl.R10_FullScanTableRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R10_FullScanTableRuleTest {

    static final class MockExplainAdapter implements ExplainAdapter {
        final Map<String, ExplainResult> results = new HashMap<>();
        int calls = 0;

        @Override
        public Optional<ExplainResult> explain(QueryExecution execution) {
            calls++;
            return Optional.ofNullable(results.get(execution.fingerprint()));
        }
    }

    private QueryStats stats(String fp, long count) {
        return new QueryStats(fp, count, Set.of("orders"), Set.of("status"), true);
    }

    private QueryExecution sample(String fp) {
        return new QueryExecution(
                "select * from orders where status = 'A'",
                1, Instant.now(), "ctx", fp, true,
                List.of(List.of(new QueryParam(1, "A"))), null);
    }

    private ExplainResult fullScan(long rows) {
        return new ExplainResult(true, "orders", rows, 1234.5, "Seq Scan");
    }

    @Test
    void fires_on_large_full_scan() {
        String fp = "select * from orders where status = ?";
        MockExplainAdapter mock = new MockExplainAdapter();
        mock.results.put(fp, fullScan(200_000));

        AnalysisContext ctx = new AnalysisContext(null, List.of(stats(fp, 100)), List.of(sample(fp)));
        List<Finding> findings = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10)).analyze(ctx);

        assertEquals(1, findings.size());
        Finding f = findings.getFirst();
        assertEquals(R10_FullScanTableRule.ID, f.ruleId());
        assertEquals("orders", f.table());
        assertEquals(fp, f.fingerprint());
        assertEquals(200_000L, f.details().get("estimatedRows"));
        assertEquals("Seq Scan", f.details().get("nodeType"));
    }

    @Test
    void does_not_fire_when_estimated_rows_below_threshold() {
        String fp = "select * from orders where status = ?";
        MockExplainAdapter mock = new MockExplainAdapter();
        mock.results.put(fp, fullScan(500)); // < min 10_000

        R10_FullScanTableRule rule = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10));
        AnalysisContext ctx = new AnalysisContext(null, List.of(stats(fp, 100)), List.of(sample(fp)));

        assertTrue(rule.analyze(ctx).isEmpty());
        assertTrue(rule.analyze(ctx).isEmpty());
        assertEquals(1, mock.calls, "dưới ngưỡng cache empty -> chỉ explain 1 lần");
    }

    @Test
    void empty_explain_result_is_not_cached_and_retried() {
        String fp = "select * from orders where status = ?";
        MockExplainAdapter mock = new MockExplainAdapter();

        R10_FullScanTableRule rule = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10));
        AnalysisContext ctx = new AnalysisContext(null, List.of(stats(fp, 100)), List.of(sample(fp)));

        assertTrue(rule.analyze(ctx).isEmpty());
        assertTrue(rule.analyze(ctx).isEmpty());
        assertEquals(2, mock.calls, "adapter không plan được (empty) = chưa biết -> KHÔNG cache, thử lại");
    }

    @Test
    void caches_verdict_and_does_not_reexplain() {
        String fp = "select * from orders where status = ?";
        MockExplainAdapter mock = new MockExplainAdapter();
        mock.results.put(fp, fullScan(200_000));

        R10_FullScanTableRule rule = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10));
        AnalysisContext ctx = new AnalysisContext(null, List.of(stats(fp, 100)), List.of(sample(fp)));

        assertEquals(1, rule.analyze(ctx).size());
        assertEquals(1, rule.analyze(ctx).size(), "chu kỳ 2 vẫn emit từ cache");
        assertEquals(1, mock.calls, "chỉ explain 1 lần cho fingerprint đã biết");
    }

    @Test
    void budget_defers_unknown_fingerprint_without_losing_it() {
        String fp1 = "select * from orders where status = ?";
        String fp2 = "select * from orders where region = ?";
        MockExplainAdapter mock = new MockExplainAdapter();
        mock.results.put(fp1, fullScan(200_000));
        mock.results.put(fp2, fullScan(300_000));

        R10_FullScanTableRule rule = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 1));
        AnalysisContext ctx = new AnalysisContext(
                null,
                List.of(stats(fp1, 100), stats(fp2, 50)),
                List.of(sample(fp1), sample(fp2)));

        assertEquals(1, rule.analyze(ctx).size(), "chu kỳ 1: chỉ fp1 (hết budget cho fp2)");
        assertEquals(1, mock.calls);

        assertEquals(2, rule.analyze(ctx).size(), "chu kỳ 2: fp1 từ cache + fp2 explain mới -> cả 2");
        assertEquals(2, mock.calls, "fp2 được hoãn sang chu kỳ 2, KHÔNG mất");
    }

    @Test
    void missing_param_sample_is_not_cached() {
        String fp = "select * from orders where status = ?";
        MockExplainAdapter mock = new MockExplainAdapter();
        mock.results.put(fp, fullScan(200_000));

        R10_FullScanTableRule rule = new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10));

        AnalysisContext noSample = new AnalysisContext(null, List.of(stats(fp, 100)), List.of());
        assertTrue(rule.analyze(noSample).isEmpty());
        assertEquals(0, mock.calls, "thiếu param -> không explain");

        AnalysisContext withSample = new AnalysisContext(null, List.of(stats(fp, 100)), List.of(sample(fp)));
        assertEquals(1, rule.analyze(withSample).size());
        assertEquals(1, mock.calls);
    }
}
