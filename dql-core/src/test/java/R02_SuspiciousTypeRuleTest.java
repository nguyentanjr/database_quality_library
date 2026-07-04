import org.example.enums.Severity;
import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R02_SuspiciousTypeRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R02_SuspiciousTypeRuleTest {

    private AnalysisContext ctxWithColumn(String columnName, String type) {
        Table table = new Table(
                "t",
                List.of("id"),
                List.of(new Column(columnName, type, false)),
                List.of(),
                List.of());
        return new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());
    }

    @Test
    void monetary_float_is_critical() {
        List<Finding> findings = new R02_SuspiciousTypeRule().analyze(ctxWithColumn("amount", "float8"));

        assertEquals(1, findings.size());
        assertEquals(R02_SuspiciousTypeRule.ID, findings.getFirst().ruleId());
        assertEquals(Severity.CRITICAL, findings.getFirst().severity());
        assertEquals("amount", findings.getFirst().column());
    }

    @Test
    void temporal_varchar_fires_high() {
        List<Finding> findings = new R02_SuspiciousTypeRule().analyze(ctxWithColumn("created_at", "varchar"));

        assertEquals(1, findings.size());
        assertEquals(Severity.HIGH, findings.getFirst().severity());
        assertEquals("created_at", findings.getFirst().column());
    }

    @Test
    void correct_types_do_not_fire() {
        // tiền lưu numeric (đúng) + thời gian lưu timestamp (đúng) + cột thường -> không bắt
        Table table = new Table(
                "t",
                List.of("id"),
                List.of(
                        new Column("balance", "numeric", false),
                        new Column("created_at", "timestamp", false),
                        new Column("description", "text", false)),
                List.of(),
                List.of());
        AnalysisContext ctx = new AnalysisContext(new SchemaSnapshot(List.of(table)), List.of(), List.of());

        assertTrue(new R02_SuspiciousTypeRule().analyze(ctx).isEmpty());
    }
}
