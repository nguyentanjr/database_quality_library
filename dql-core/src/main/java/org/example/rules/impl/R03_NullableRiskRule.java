package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.*;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.NullableRiskConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class R03_NullableRiskRule implements Rule {
    public static final String ID = "R03_NullableRisk";
    public static final Severity SEVERITY = Severity.MEDIUM;

    private final NullableRiskConfig config;

    public R03_NullableRiskRule() {
        this(NullableRiskConfig.defaults());
    }

    public R03_NullableRiskRule(NullableRiskConfig config) {
        this.config = config;
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        SchemaSnapshot snapshot = context.snapshot();
        List<Table> tables = snapshot.tables();

        for(Table table : tables) {
            Set<String> fkColumns = table.foreignKeys()
                    .stream()
                    .map(ForeignKey::column)
                    .collect(Collectors.toSet());

            for(Column column : table.columns()) {
                if (!column.nullable()) {
                    continue;
                }
                Severity configSeverity = severityFromConfig(column.name());
                if(fkColumns.contains(column.name())) {
                    findings.add(new Finding(
                            ID,
                            SEVERITY,
                            "Foreign key column is nullable",
                            "Make foreign key column NOT NULL if the relationship is required",
                            table.name(),
                            column.name(),
                            null,
                            Map.of()
                    ));
                }
                else if(configSeverity != null) {
                    findings.add(new Finding(
                            ID,
                            configSeverity,
                            "Important column is nullable",
                            "Consider adding NOT NULL if this column is required by business logic",
                            table.name(),
                            column.name(),
                            null,
                            Map.of()
                    ));
                }

            }
        }

        return findings;
    }

    private Severity severityFromConfig(String columnName) {

        for(Map.Entry<Severity, List<String>> entry : config.columnNameRules().entrySet()) {
            for(String keyword : entry.getValue()) {
                if(columnName.equals(keyword)) return entry.getKey();
            }
        }
        return null;
    }
}
