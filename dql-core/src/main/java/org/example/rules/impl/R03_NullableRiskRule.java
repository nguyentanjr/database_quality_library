package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.ForeignKey;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.List;

public class R03_NullableRiskRule implements Rule {
    public static final String ID = "R03_NullableRisk";
    public static final Severity SEVERITY = Severity.HIGH;

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        SchemaSnapshot snapshot = context.snapshot();
        List<Table> tables = snapshot.tables();
        for(Table table : tables) {
            List<ForeignKey> foreignKeys = table.foreignKeys();
        }
    }
}
