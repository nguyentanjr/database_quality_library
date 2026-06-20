package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class R04_SelectStarRule implements Rule {

    public static final String ID = "R04_SelectStar";

    public static final Severity SEVERITY = Severity.LOW ;

    private static final Pattern SELECT_STAR_PATTERN =
            Pattern.compile("^select\\s+\\*\\s+from\\b");


    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();

        List<QueryStats> queryStats = context.queryStats();

        for(QueryStats stats : queryStats) {
            String fingerprint = stats.fingerprint();
            if (isSelectStar(fingerprint)) {
                findings.add(new Finding(
                        ID,
                        SEVERITY,
                        "Query uses SELECT *",
                        "Avoid SELECT *; explicitly list required columns",
                        null,
                        null,
                        fingerprint,
                        Map.of()
                ));
            }
        }

        return findings;
    }

    private boolean isSelectStar(String fingerprint) {
        return SELECT_STAR_PATTERN.matcher(fingerprint).find();
    }
}
