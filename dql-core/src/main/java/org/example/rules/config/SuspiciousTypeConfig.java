package org.example.rules.config;

import org.example.enums.Severity;

import java.util.List;
import java.util.Set;


public record SuspiciousTypeConfig(List<TypeRule> rules) {

    public record TypeRule(
            List<String> columns,
            Set<String> badTypes,
            Severity severity,
            String message,
            String suggestion
    ) {
    }

    public static SuspiciousTypeConfig defaults() {
        return new SuspiciousTypeConfig(List.of(
                new TypeRule(
                        List.of("amount", "price", "balance", "cost", "fee", "total"),
                        Set.of("float8", "float4", "double", "real"),
                        Severity.CRITICAL,
                        "Monetary column uses floating-point type",
                        "Use NUMERIC or DECIMAL for monetary values"
                ),
                new TypeRule(
                        List.of("_at", "_date", "_time", "created", "updated"),
                        Set.of("varchar", "text", "char", "bpchar"),
                        Severity.MEDIUM,
                        "Temporal column uses string type",
                        "Use TIMESTAMP, DATE, or TIME instead"
                )
        ));
    }
}
