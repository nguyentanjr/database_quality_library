import org.example.model.Finding;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;
import org.example.rules.impl.R04_SelectStarRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R04_SelectStarRuleTest {

    private AnalysisContext ctx(String fingerprint) {
        QueryStats stats = new QueryStats(fingerprint, 1, Set.of("accounts"), Set.of(), true);
        return new AnalysisContext(null, List.of(stats), List.of());
    }

    @Test
    void fires_on_select_star() {
        List<Finding> findings = new R04_SelectStarRule().analyze(ctx("select * from accounts where id = ?"));

        assertEquals(1, findings.size());
        assertEquals(R04_SelectStarRule.ID, findings.getFirst().ruleId());
        assertEquals("select * from accounts where id = ?", findings.getFirst().fingerprint());
    }

    @Test
    void does_not_fire_on_count_star() {
        // count(*) không phải SELECT * — regex ^select\s+\*\s+from KHÔNG được match nhầm
        assertTrue(new R04_SelectStarRule().analyze(ctx("select count(*) from accounts")).isEmpty());
    }

    @Test
    void does_not_fire_on_explicit_columns() {
        assertTrue(new R04_SelectStarRule().analyze(ctx("select id, email from accounts")).isEmpty());
    }
}
