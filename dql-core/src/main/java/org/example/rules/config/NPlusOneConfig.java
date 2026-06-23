package org.example.rules.config;

public record NPlusOneConfig(
        int minCount,
        int minDistinctParams
) {
    public static NPlusOneConfig defaults() {
        return new NPlusOneConfig(5, 3);
    }
}