package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.List;

public final class R01_MissingPrimaryKeyRule implements Rule {

    public static final String ID = "R01_MissingPrimaryKey";
    public static final Severity SEVERITY = Severity.HIGH;


    @Override
    public List<Finding> analyze(AnalysisContext context) {

        List<Finding> findings = new ArrayList<>();
        List<Table> tables = context.snapshot().tables();
        for (Table table : tables) {
            if (table.primaryKey().isEmpty()) {
                findings.add(new Finding(
                        ID,
                        SEVERITY,
                        "Table has no primary key",
                        "Add a primary key to the table",
                        table.name(),
                        null,
                        null));
            }
        }
        return findings;
    }
}