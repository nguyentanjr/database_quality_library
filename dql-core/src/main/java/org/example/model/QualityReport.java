package org.example.model;

import java.util.List;
import java.util.Map;

public record QualityReport(List<Finding> findings, int score, Map<String, Object> metrics) {
}
