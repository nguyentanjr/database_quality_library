package org.example.rules.config;

import org.example.enums.Severity;

import java.util.EnumMap;
import java.util.Map;

public record ScoreConfig(Map<Severity, Integer> weights) {

    public int weight(Severity severity) {
        return weights.getOrDefault(severity, 0);
    }

    public static ScoreConfig defaults() {
        Map<Severity, Integer> defaults = new EnumMap<>(Severity.class);
        defaults.put(Severity.CRITICAL, 15);
        defaults.put(Severity.HIGH, 8);
        defaults.put(Severity.MEDIUM, 3);
        defaults.put(Severity.LOW, 1);
        return new ScoreConfig(defaults);
    }
}
