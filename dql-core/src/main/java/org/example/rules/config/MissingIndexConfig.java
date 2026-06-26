package org.example.rules.config;

public record MissingIndexConfig(
        long thresholdCount
) {
    public static MissingIndexConfig defaults() {
        return new MissingIndexConfig(5);
    }
}