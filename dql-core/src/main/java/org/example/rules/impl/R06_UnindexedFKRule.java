package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.*;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class R06_UnindexedFKRule implements Rule {

    public static final String ID = "R06_UnindexedFK";

    public static final Severity SEVERITY = Severity.MEDIUM;


    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        SchemaSnapshot snapshot = context.snapshot();
        List<Table> tables = snapshot.tables();

        for(Table table : tables) {
            List<ForeignKey> foreignKeys = table.foreignKeys();
            List<Index> indices = table.indexes();
            Set<String> leadingIndices = RuleHelper.leadingIndices(indices);
            for(ForeignKey foreignKey : foreignKeys) {
                String column = foreignKey.column();
                if (column == null || column.isEmpty()) {
                    continue;
                }
                if(!leadingIndices.contains(column)) {
                    findings.add(new Finding(
                            ID,
                            SEVERITY,
                            "Foreign key column is not indexed",
                            "Add an index starting with this foreign key column",
                            table.name(),
                            column,
                            null,
                            null
                    ));
                }
            }
        }
        return findings;
    }


}
