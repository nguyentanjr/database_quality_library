import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.Index;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R08_UnusedIndexRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R08_UnusedIndexRuleTest {

    private Table accounts(List<Index> indexes) {
        return new Table(
                "accounts",
                List.of("id"),
                List.of(new Column("id", "bigint", false), new Column("email", "varchar", false)),
                List.of(),
                indexes);
    }

    @Test
    void fires_when_index_leading_column_never_queried() {
        Table table = accounts(List.of(new Index("idx_email", List.of("email"), false)));
        // workload chỉ filter theo id, không đụng email
        QueryStats stats = new QueryStats("select * from accounts where id = ?", 5,
                Set.of("accounts"), Set.of("id"), true);
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(stats), List.of());

        List<Finding> findings = new R08_UnusedIndexRule().analyze(ctx);

        assertEquals(1, findings.size());
        assertEquals(R08_UnusedIndexRule.ID, findings.getFirst().ruleId());
        assertEquals("email", findings.getFirst().column());
        assertEquals("idx_email", findings.getFirst().details().get("indexName"));
    }

    @Test
    void does_not_fire_when_index_column_is_used() {
        Table table = accounts(List.of(new Index("idx_email", List.of("email"), false)));
        QueryStats stats = new QueryStats("select * from accounts where email = ?", 5,
                Set.of("accounts"), Set.of("email"), true);
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(stats), List.of());

        assertTrue(new R08_UnusedIndexRule().analyze(ctx).isEmpty());
    }

    @Test
    void skips_primary_key_and_unique_indexes() {
        // pk index (leading = PK col) và unique index đều bị skip dù không được query
        Table table = accounts(List.of(
                new Index("accounts_pkey", List.of("id"), true),
                new Index("uq_email", List.of("email"), true)));
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        assertTrue(new R08_UnusedIndexRule().analyze(ctx).isEmpty());
    }
}
