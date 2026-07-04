import org.example.analyzer.RuleEngine;
import org.example.enums.Severity;
import org.example.explain.ExplainAdapter;
import org.example.model.Column;
import org.example.model.ExplainResult;
import org.example.model.Finding;
import org.example.model.ForeignKey;
import org.example.model.Index;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Micro-benchmark thời gian analysis (RuleEngine.run) theo #fingerprints × #recentExecutions.
 *
 * KHÔNG phải test đúng/sai (không đặt tên *Test để không chạy trong `mvn test` thường).
 * Chạy: mvn -pl dql-core test -Dtest=RuleEngineBenchmark
 *
 * R10 dùng MOCK ExplainAdapter (verdict in-memory, KHÔNG EXPLAIN DB) → loại chi phí I/O
 * khỏi phép đo; cache verdict warm trong warmup (giống scheduler tái dùng bean cả đời process).
 * Số in ra là steady-state analysis time (JIT đã warm).
 */
class RuleEngineBenchmark {

    private static final int TABLE_COUNT = 20;
    private static final int WARMUP = 30;
    private static final int MEASURE = 100;

    @Test
    void benchmark_analysis_time_grid() {
        RuleEngine engine = new RuleEngine(buildAllRules());
        SchemaSnapshot snapshot = buildSchema(TABLE_COUNT);

        int[] fingerprintCounts = {100, 500, 1000, 2000};
        int[] recentCounts = {100, 5000};

        System.out.println("\n=== RuleEngine analysis-time benchmark (steady-state, R10 mock) ===");
        System.out.printf("machine warmup=%d measure=%d tables=%d%n", WARMUP, MEASURE, TABLE_COUNT);
        System.out.println("| #fingerprints | #recentExec | avg ms | min ms | #findings |");
        System.out.println("|---------------|-------------|--------|--------|-----------|");

        for (int fp : fingerprintCounts) {
            for (int rec : recentCounts) {
                List<QueryStats> stats = buildStats(fp);
                List<QueryExecution> recents = buildRecents(rec);
                AnalysisContext ctx = new AnalysisContext(snapshot, stats, recents);

                int findings = engine.run(ctx).size();

                for (int i = 0; i < WARMUP; i++) engine.run(ctx);

                long best = Long.MAX_VALUE;
                long total = 0;
                for (int i = 0; i < MEASURE; i++) {
                    long t0 = System.nanoTime();
                    engine.run(ctx);
                    long dt = System.nanoTime() - t0;
                    best = Math.min(best, dt);
                    total += dt;
                }
                double avgMs = (total / (double) MEASURE) / 1_000_000.0;
                double minMs = best / 1_000_000.0;
                System.out.printf("| %13d | %11d | %6.3f | %6.3f | %9d |%n", fp, rec, avgMs, minMs, findings);
            }
        }
        System.out.println("=== end benchmark ===\n");
    }

    // ---- rule wiring (R10 mock adapter, không I/O) ----
    private List<Rule> buildAllRules() {
        ExplainAdapter mock = execution -> {
            String f = execution.fingerprint();
            if (f != null && f.contains("t_big")) {
                return Optional.of(new ExplainResult(true, "t_big", 50_000, 100.0, "Seq Scan"));
            }
            return Optional.of(new ExplainResult(false, "t", 10, 1.0, "Index Scan"));
        };
        return List.of(
                new R01_MissingPrimaryKeyRule(),
                new R02_SuspiciousTypeRule(),
                new R03_NullableRiskRule(),
                new R04_SelectStarRule(),
                new R05_SlowQueryRule(new SlowQueryConfig(1000)),
                new R06_UnindexedFKRule(),
                new R07_MissingIndexRule(new MissingIndexConfig(5)),
                new R08_UnusedIndexRule(),
                new R09_NPlusOneRule(new NPlusOneConfig(5, 3)),
                new R10_FullScanTableRule(mock, new FullScanConfig(10_000, 10))
        );
    }

    // ---- synthetic schema (findings schema-only cố định, không scale theo N) ----
    private SchemaSnapshot buildSchema(int tableCount) {
        List<Table> tables = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) {
            String name = (i == tableCount - 1) ? "t_big" : "t" + i;
            List<String> pk = (i == 0) ? List.of() : List.of("id"); // t0 thiếu PK -> R01
            List<Column> cols = List.of(
                    new Column("id", "int8", false),
                    new Column("amount", "float8", false),       // R02 CRITICAL
                    new Column("created_at", "varchar", true),   // R02 HIGH
                    new Column("email", "varchar", true),
                    new Column("col_a", "int8", false),
                    new Column("status", "varchar", true),
                    new Column("account_id", "int8", true)       // nullable FK -> R03
            );
            List<ForeignKey> fks = (i == 0)
                    ? List.of()
                    : List.of(new ForeignKey("account_id", "t" + (i - 1), "id")); // unindexed -> R06
            List<Index> idx = List.of(new Index("idx_" + name + "_col_a", List.of("col_a"), false)); // unused -> R08
            tables.add(new Table(name, pk, cols, fks, idx));
        }
        return new SchemaSnapshot(tables);
    }

    // ---- synthetic queryStats (scale N): R04 (select*), R07 (col không index, count cao) ----
    private List<QueryStats> buildStats(int n) {
        List<QueryStats> stats = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            int k = j % TABLE_COUNT;
            String table = (k == TABLE_COUNT - 1) ? "t_big" : "t" + k;
            String fp = "select * from " + table + " where status = ?" + " /*" + j + "*/";
            stats.add(new QueryStats(fp, 50, Set.of(table), Set.of("status"), true));
        }
        return stats;
    }

    // ---- synthetic recentExecutions (scale M): R05 (slow), R09 (N+1), R10 sample ----
    private List<QueryExecution> buildRecents(int m) {
        List<QueryExecution> recents = new ArrayList<>(m);
        for (int j = 0; j < m; j++) {
            int k = j % TABLE_COUNT;
            String table = (k == TABLE_COUNT - 1) ? "t_big" : "t" + k;
            String fp = "select * from " + table + " where status = ?";
            String contextId = "ctx-" + (j % 50);                 // 50 context -> nhiều nhóm N+1
            long duration = (j % 20 == 0) ? 1500 : 5;             // ~5% slow -> R05
            recents.add(new QueryExecution(
                    "select * from " + table + " where status = 'X" + j + "'",
                    duration, Instant.now(), contextId, fp, true,
                    List.of(List.of(new QueryParam(1, "X" + j))), null));
        }
        return recents;
    }
}
