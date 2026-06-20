package org.example.model;

import org.example.enums.Severity;

import java.util.Map;

public record Finding(String ruleId, Severity severity, String message, String suggestion, String table, String column,
                      String fingerprint, Map<String, Object> details) {
}
