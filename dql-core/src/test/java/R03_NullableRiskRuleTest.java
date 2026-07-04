import org.example.enums.Severity;
import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.ForeignKey;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R03_NullableRiskRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R03_NullableRiskRuleTest {

    @Test
    void nullable_foreign_key_fires() {
        Table table = new Table(
                "transactions",
                List.of("id"),
                List.of(new Column("account_id", "bigint", true)),
                List.of(new ForeignKey("account_id", "accounts", "id")),
                List.of());
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        List<Finding> findings = new R03_NullableRiskRule().analyze(ctx);

        assertEquals(1, findings.size());
        // Nullable-FK xếp HIGH (rủi ro integrity), cao hơn "important column nullable" (config=MEDIUM).
        assertEquals(Severity.HIGH, findings.getFirst().severity());
        assertEquals("Foreign key column is nullable", findings.getFirst().message());
        assertEquals("account_id", findings.getFirst().column());
    }

    @Test
    void config_listed_nullable_column_fires_configured_severity() {
        // default columnNameRules: MEDIUM -> [email, username, status, code], khớp CHÍNH XÁC tên cột
        Table table = new Table(
                "accounts",
                List.of("id"),
                List.of(new Column("email", "varchar", true)),
                List.of(),
                List.of());
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        List<Finding> findings = new R03_NullableRiskRule().analyze(ctx);

        assertEquals(1, findings.size());
        assertEquals(Severity.MEDIUM, findings.getFirst().severity());
        assertEquals("email", findings.getFirst().column());
    }

    @Test
    void nullable_column_outside_config_and_not_fk_does_not_fire() {
        Table table = new Table(
                "accounts",
                List.of("id"),
                List.of(new Column("description", "text", true)),
                List.of(),
                List.of());
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        assertTrue(new R03_NullableRiskRule().analyze(ctx).isEmpty());
    }

    @Test
    void not_null_column_does_not_fire() {
        Table table = new Table(
                "accounts",
                List.of("id"),
                List.of(new Column("email", "varchar", false)),
                List.of(),
                List.of());
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        assertTrue(new R03_NullableRiskRule().analyze(ctx).isEmpty());
    }
}
