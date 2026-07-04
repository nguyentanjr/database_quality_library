package org.example.analyzer;

import org.example.model.Finding;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RuleEngine {

    private static final Logger LOGGER = Logger.getLogger(RuleEngine.class.getName());

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Finding> run(AnalysisContext context) {
        List<Finding> allFindings = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                List<Finding> findings = rule.analyze(context);
                if (findings != null) {
                    allFindings.addAll(findings);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "DQL rule " + rule.id() + " failed; skipping it this cycle", e);
            }
        }
        return allFindings;
    }

}
