package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.*;

public class R09_NPlusOneRule implements Rule {

    public static final String ID = "R09_NPlusOne";

    public static final Severity SEVERITY = Severity.HIGH;

    private final NPlusOneConfig config;

    public R09_NPlusOneRule(NPlusOneConfig config) {
        this.config = config;
    }


    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        Map<Key, NPlusOneAccumulator> groups = groupByContextAndFingerprint(context.recentExecutions());

        for (Map.Entry<Key, NPlusOneAccumulator> entry : groups.entrySet()) {
            NPlusOneAccumulator acc = entry.getValue();
            Key key = entry.getKey();
            int countDistinctParams = acc.distinctParamKeys.size();
            if (acc.getCount() >= config.threshold()
                    && countDistinctParams >= config.threshold())  {
                findings.add(new Finding(
                        ID,
                        SEVERITY,
                        "Possible N+1 query detected",
                        "Batch this query using IN (...) or fetch the association eagerly where appropriate",
                        null,
                        null,
                        key.fingerprint(),
                        Map.of(
                                "contextId",key.contextId(),
                                "count", acc.getCount(),
                                "distinctParams", countDistinctParams
                        )
                ));
            }
        }

        return findings;

    }

    private Map<Key, NPlusOneAccumulator> groupByContextAndFingerprint(List<QueryExecution> recentExecutions) {
        Map<Key, NPlusOneAccumulator> groups  = new HashMap<>();
        for(QueryExecution execution : recentExecutions) {
            if (execution.contextId() == null || execution.fingerprint() == null) continue;
            Key key = new Key(execution.contextId(), execution.fingerprint());
            NPlusOneAccumulator acc =  groups.computeIfAbsent(key, k -> new NPlusOneAccumulator());

            List<List<QueryParam>> queryParams = execution.paramSets();
            for(List<QueryParam> list : queryParams) {
                if (list.isEmpty()) continue;
                List<String> values = new ArrayList<>();
                for(QueryParam queryParam : list) {
                     values.add(queryParam.value());
                }
                String paramSets = String.join("|", values);
                acc.addParamKey(paramSets);
            }
            acc.incrementCount();
        }
       return groups ;

    }

    private record Key(
            String contextId,
            String fingerprint
    ) {
    }

    private static class NPlusOneAccumulator {
        private int count = 0;
        private Set<String> distinctParamKeys = new HashSet<>();

        public void incrementCount() {
            this.count++;
        }

        public void addParamKey(String key) {
            distinctParamKeys.add(key);
        }

        public int getCount() {
            return count;
        }

    }
}
