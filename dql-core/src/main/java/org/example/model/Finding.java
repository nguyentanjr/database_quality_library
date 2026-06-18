package org.example.model;

import org.example.enums.Severity;

public record Finding(String ruleId, Severity severity, String message, String suggestion, String table, String column,
                      String fingerprint) {
}
