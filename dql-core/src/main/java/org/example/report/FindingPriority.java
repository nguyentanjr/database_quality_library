package org.example.report;

import org.example.model.Finding;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

public final class FindingPriority {

    private FindingPriority() {
    }

    public static List<Finding> sortedBySeverity(List<Finding> findings) {
        return findings.stream()
                .sorted(Comparator.comparingInt((Finding f) -> f.severity().getScore()).reversed())
                .toList();
    }

    public static List<Finding> top(List<Finding> findings, int n) {
        return findings.stream()
                .sorted(Comparator.comparingInt((Finding f) -> f.severity().getScore()).reversed())
                .limit(n)
                .toList();
    }
}
