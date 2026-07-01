package org.example.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QualityReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonReporter {

    private static final int TOP_FINDINGS = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QualityReport buildReport(
            List<Finding> findings,
            int tablesScanned
    ) {
        List<Finding> sorted = FindingPriority.sortedBySeverity(findings);
        return new QualityReport(
                sorted,
                score(sorted),
                metrics(sorted, tablesScanned)
        );
    }


    public String report(List<Finding> findings, int tablesScanned) {
        QualityReport report = buildReport(findings, tablesScanned);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize QualityReport", e);
        }
    }

    private int score(List<Finding> findings) {
        int penaltyScore = 0;
        for(Finding finding : findings) {
            penaltyScore += finding.severity().getScore();
        }
        return Math.max(0, 100 - penaltyScore);
    }

    private Map<String, Object> metrics(List<Finding> findings, int tablesScanned) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for(Severity severity : Severity.values()) {
            long count = findings.stream()
                    .filter(f -> f.severity() == severity)
                    .count();
            metrics.put(severity.name().toLowerCase(), count);
        }
        metrics.put("tablesAnalyzed", tablesScanned);
        return metrics;
    }
}
