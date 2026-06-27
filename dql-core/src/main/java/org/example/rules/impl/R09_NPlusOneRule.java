package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.NPlusOneConfig;

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
        Map<Key, NPlusOneAccumulator> groups =
                groupByContextAndFingerprint(context.recentExecutions());

        Map<String, R09Summary> summaries = new LinkedHashMap<>();

        for (Map.Entry<Key, NPlusOneAccumulator> entry : groups.entrySet()) {
            Key key = entry.getKey();
            NPlusOneAccumulator acc = entry.getValue();

            int count = acc.getCount();
            int distinctParams = acc.distinctParamKeys.size();

            if (count >= config.minCount()
                    && distinctParams >= config.minDistinctParams()) {

                summaries.computeIfAbsent(
                        key.fingerprint(),
                        R09Summary::new
                ).add(count, distinctParams);
            }
        }

        return summaries.values()
                .stream()
                .map(R09Summary::toFinding)
                .toList();
    }

    private Map<Key, NPlusOneAccumulator> groupByContextAndFingerprint(List<QueryExecution> recentExecutions) {
        Map<Key, NPlusOneAccumulator> groups  = new HashMap<>();
        for(QueryExecution execution : recentExecutions) {
            if (execution.contextId() == null || execution.fingerprint() == null) continue;
            Key key = new Key(execution.contextId(), execution.fingerprint());
            NPlusOneAccumulator acc =  groups.computeIfAbsent(key, k -> new NPlusOneAccumulator());

            List<List<QueryParam>> queryParams = execution.paramSets();
            boolean hasParam = false;
            for(List<QueryParam> list : queryParams) {
                if (list.isEmpty()) continue;

                hasParam = true;

                List<String> values = new ArrayList<>();
                for(QueryParam queryParam : list) {
                     values.add(queryParam.value().toString());
                }

                String paramKey = String.join("|", values);
                acc.addParamKey(paramKey);
            }
            if (hasParam) {
                acc.incrementCount();
            }
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

    private static class R09Summary {
        private final String fingerprint;
        private int requestsAffected = 0;
        private int maxCount = 0;
        private int maxDistinctParams = 0;

        R09Summary(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        void add(int count, int distinctParams) {
            this.requestsAffected++;
            this.maxCount = Math.max(maxCount, count);
            this.maxDistinctParams = Math.max(maxDistinctParams, distinctParams);
        }

        Finding toFinding() {
            return new Finding(
                    ID,
                    SEVERITY,
                    "Possible N+1 query detected",
                    "Batch this query using IN (...) or fetch the association eagerly where appropriate",
                    null,
                    null,
                    fingerprint,
                    Map.of(
                            "requestsAffected", requestsAffected,
                            "maxCount", maxCount,
                            "maxDistinctParams", maxDistinctParams
                    )
            );
        }
    }
}
