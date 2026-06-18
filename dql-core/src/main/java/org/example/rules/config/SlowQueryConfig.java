package org.example.rules.config;

public record SlowQueryConfig(
        long thresholdMs
) {
    public static SlowQueryConfig defaults() {
        return new SlowQueryConfig(5000);
    }
}