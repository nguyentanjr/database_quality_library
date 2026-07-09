package org.example.report;

import org.example.enums.Severity;
import org.example.model.Finding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class FindingPriority {

    private FindingPriority() {
    }

    private static final Comparator<Finding> BY_SEVERITY_RANK =
            Comparator.comparingInt(f -> f.severity().ordinal());

    public static List<Finding> sortedBySeverity(List<Finding> findings) {
        return findings.stream()
                .sorted(BY_SEVERITY_RANK)
                .toList();
    }


    public static List<Finding> topEnsuringSevere(List<Finding> findings, int softCap) {
        List<Finding> result = new ArrayList<>();
        for (Finding finding : sortedBySeverity(findings)) {
            boolean severe = finding.severity() == Severity.CRITICAL
                    || finding.severity() == Severity.HIGH;
            if (severe || result.size() < softCap) {
                result.add(finding);
            }
        }
        return result;
    }
}
