import org.example.model.Finding;
import org.example.model.Index;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.model.StatsAccumulator;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.impl.R07_MissingIndexRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R07_MissingIndexRuleTest {

    @Test
    void ignores_join_columns_collected_from_join_on() {
        String fingerprint = """
                select * from transactions t
                join accounts a on t.account_id = a.id
                where t.amount > ?
                """;
        StatsAccumulator acc = StatsAccumulator.parseFrom(fingerprint);
        QueryStats stats = new QueryStats(
                fingerprint,
                100,
                acc.getTables(),
                acc.getColumns(),
                acc.isSelect()
        );

        AnalysisContext context = new AnalysisContext(
                new SchemaSnapshot(List.of(
                        table("transactions"),
                        table("accounts")
                )),
                List.of(stats),
                List.of()
        );

        assertTrue(stats.columns().contains("account_id"));
        assertTrue(stats.columns().contains("amount"));
        assertTrue(stats.columns().contains("id"));

        List<Finding> findings = new R07_MissingIndexRule(new MissingIndexConfig(1))
                .analyze(context);

        assertTrue(findings.isEmpty());
    }

    @Test
    void reports_single_table_filtered_column_without_leading_index() {
        QueryStats stats = new QueryStats(
                "select * from accounts where email = ?",
                100,
                Set.of("accounts"),
                Set.of("email"),
                true
        );
        AnalysisContext context = new AnalysisContext(
                new SchemaSnapshot(List.of(table("accounts"))),
                List.of(stats),
                List.of()
        );

        List<Finding> findings = new R07_MissingIndexRule(new MissingIndexConfig(1))
                .analyze(context);

        assertEquals(1, findings.size());
        assertEquals("accounts", findings.getFirst().table());
        assertEquals("email", findings.getFirst().column());
    }

    @Test
    void does_not_report_when_column_has_leading_index() {
        Table accounts = new Table(
                "accounts",
                List.of("id"),
                List.of(),
                List.of(),
                List.of(new Index("idx_email", List.of("email"), false)));
        QueryStats stats = new QueryStats(
                "select * from accounts where email = ?",
                100,
                Set.of("accounts"),
                Set.of("email"),
                true);
        AnalysisContext context = new AnalysisContext(
                new SchemaSnapshot(List.of(accounts)), List.of(stats), List.of());

        assertTrue(new R07_MissingIndexRule(new MissingIndexConfig(1)).analyze(context).isEmpty());
    }

    @Test
    void does_not_report_when_count_below_threshold() {
        QueryStats stats = new QueryStats(
                "select * from accounts where email = ?",
                4,
                Set.of("accounts"),
                Set.of("email"),
                true);
        AnalysisContext context = new AnalysisContext(
                new SchemaSnapshot(List.of(table("accounts"))), List.of(stats), List.of());

        // threshold 5 > count 4 -> không xét
        assertTrue(new R07_MissingIndexRule(new MissingIndexConfig(5)).analyze(context).isEmpty());
    }

    private Table table(String name) {
        return new Table(name, List.of("id"), List.of(), List.of(), List.of());
    }
}
