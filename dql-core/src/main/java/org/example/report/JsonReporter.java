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

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QualityReport buildReport(
            List<Finding> findings,
            int tablesScanned
    ) {
        return new QualityReport(
                findings,
                score(findings),
                metrics(findings, tablesScanned)
        );
    }


    public String report(List<Finding> findings, int tableScanned) throws JsonProcessingException {
        QualityReport report = new QualityReport(findings, score(findings), metrics(findings, tableScanned));
    try {
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(report);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("JsonProcessingException");
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
        metrics.put("tablesScanned", tablesScanned);
        return metrics;
    }
}
