package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.*;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.MissingIndexConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class R07_MissingIndexRule implements Rule {

    public static final String ID = "R07_MissingIndex";

    public static final Severity SEVERITY = Severity.MEDIUM;

    private final MissingIndexConfig config;

    public R07_MissingIndexRule(MissingIndexConfig config) {
        this.config = config;
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        SchemaSnapshot snapshot = context.snapshot();
        List<Table> tables = snapshot.tables();
        List<QueryStats> queryStats = context.queryStats();
        for (Table table : tables) {
            List<Index> indices = table.indexes();
            Set<String> leadingIndices = RuleHelper.leadingIndices(indices);
            for (QueryStats stats : queryStats) {
                if (stats.count() < config.thresholdCount()) {
                    continue;
                }

                //TODO:
                if(stats.tables().size() > 1) {
                    continue;
                }

                if (!stats.tables().contains(table.name())) {
                    continue;
                }
                for (String column : stats.columns()) {
                    if (!leadingIndices.contains(column)) {
                        findings.add(new Finding(
                                ID,
                                SEVERITY,
                                "Frequently filtered column is not indexed",
                                "Add an index starting with this column",
                                table.name(),
                                column,
                                null,
                                Map.of()
                        ));
                    }
                }
            }
        }
        return findings;
    }

}
