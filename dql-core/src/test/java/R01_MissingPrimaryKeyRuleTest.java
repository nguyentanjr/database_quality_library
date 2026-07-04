import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R01_MissingPrimaryKeyRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R01_MissingPrimaryKeyRuleTest {

    @Test
    void fires_when_table_has_no_primary_key() {
        Table noPk = new Table("transaction_log", List.of(), List.of(), List.of(), List.of());
        AnalysisContext ctx = new AnalysisContext(
                new SchemaSnapshot(List.of(noPk)), List.of(), List.of());

        List<Finding> findings = new R01_MissingPrimaryKeyRule().analyze(ctx);

        assertEquals(1, findings.size());
        assertEquals(R01_MissingPrimaryKeyRule.ID, findings.getFirst().ruleId());
        assertEquals("transaction_log", findings.getFirst().table());
    }

    @Test
    void does_not_fire_when_table_has_primary_key() {
        Table withPk = new Table("accounts", List.of("id"), List.of(), List.of(), List.of());
        AnalysisContext ctx = new AnalysisContext(
                new SchemaSnapshot(List.of(withPk)), List.of(), List.of());

        assertTrue(new R01_MissingPrimaryKeyRule().analyze(ctx).isEmpty());
    }
}
