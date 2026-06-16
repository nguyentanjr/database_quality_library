package org.example.analyzer;

import org.example.model.Finding;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.List;

public class RuleEngine {


    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Finding> run(AnalysisContext context) {
        List<Finding> allFindings = new ArrayList<>();
        for(Rule rule : rules) {
            allFindings.addAll(rule.analyze(context));
        }
        return allFindings;
    }

}
