package org.example.rules;

import org.example.model.Finding;

import java.util.List;

public interface Rule {
    List<Finding> analyze(AnalysisContext context);
}
