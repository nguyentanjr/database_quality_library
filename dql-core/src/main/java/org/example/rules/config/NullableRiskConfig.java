package org.example.rules.config;

import org.example.enums.Severity;

import java.util.List;
import java.util.Map;

public record NullableRiskConfig(
        Map<Severity, List<String>> columnNameRules
) {
    public static NullableRiskConfig defaults() {
        return new NullableRiskConfig(
                Map.of(
                        Severity.MEDIUM, List.of("email", "username", "status", "code")
                       // Severity.LOW, List.of("created_at", "updated_at")
                )
        );
    }
}