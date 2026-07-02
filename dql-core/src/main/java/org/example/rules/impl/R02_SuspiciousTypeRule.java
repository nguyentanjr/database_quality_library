package org.example.rules.impl;

import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.SuspiciousTypeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class R02_SuspiciousTypeRule implements Rule {

    public static final String ID = "R02_SuspiciousType";

    private final SuspiciousTypeConfig config;

    public R02_SuspiciousTypeRule() {
        this(SuspiciousTypeConfig.defaults());
    }

    public R02_SuspiciousTypeRule(SuspiciousTypeConfig config) {
        this.config = config;
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        for (Table table : context.snapshot().tables()) {
            for (Column column : table.columns()) {
                String name = column.name().toLowerCase();
                String type = column.type().toLowerCase();

                for (SuspiciousTypeConfig.TypeRule rule : config.rules()) {
                    if (matchesColumn(name, rule.columns()) && rule.badTypes().contains(type)) {
                        findings.add(new Finding(
                                ID,
                                rule.severity(),
                                rule.message(),
                                rule.suggestion(),
                                table.name(),
                                column.name(),
                                null,
                                Map.of()
                        ));
                        break; // 1 finding/column — rule đầu match thắng (giữ ưu tiên như if/else cũ)
                    }
                }
            }
        }
        return findings;
    }

    private boolean matchesColumn(String name, List<String> keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
