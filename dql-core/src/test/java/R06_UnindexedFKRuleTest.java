import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.ForeignKey;
import org.example.model.Index;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R06_UnindexedFKRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R06_UnindexedFKRuleTest {

    private AnalysisContext ctx(List<Index> indexes) {
        Table table = new Table(
                "transactions",
                List.of("id"),
                List.of(new Column("account_id", "bigint", false)),
                List.of(new ForeignKey("account_id", "accounts", "id")),
                indexes);
        return new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());
    }

    @Test
    void fires_when_fk_has_no_index() {
        List<Finding> findings = new R06_UnindexedFKRule().analyze(ctx(List.of()));

        assertEquals(1, findings.size());
        assertEquals(R06_UnindexedFKRule.ID, findings.getFirst().ruleId());
        assertEquals("account_id", findings.getFirst().column());
    }

    @Test
    void does_not_fire_when_fk_is_leading_index_column() {
        List<Index> indexes = List.of(new Index("idx_acc", List.of("account_id"), false));

        assertTrue(new R06_UnindexedFKRule().analyze(ctx(indexes)).isEmpty());
    }

    @Test
    void composite_index_counts_only_leading_column() {
        // account_id nằm ở vị trí thứ 2 của composite index -> KHÔNG tính "indexed" (B+Tree)
        List<Index> notLeading = List.of(new Index("idx_created_acc", List.of("created_at", "account_id"), false));
        assertEquals(1, new R06_UnindexedFKRule().analyze(ctx(notLeading)).size());

        // account_id ở vị trí đầu -> tính indexed
        List<Index> leading = List.of(new Index("idx_acc_created", List.of("account_id", "created_at"), false));
        assertTrue(new R06_UnindexedFKRule().analyze(ctx(leading)).isEmpty());
    }
}
