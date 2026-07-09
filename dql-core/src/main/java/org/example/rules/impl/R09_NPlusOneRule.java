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
                ).add(count, distinctParams, acc.sourceLocationCounts);
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
            if (!execution.select()) continue; // N+1 là pattern đọc lặp; bỏ write (INSERT/UPDATE/DELETE)

            List<List<QueryParam>> queryParams = execution.paramSets();
            if (queryParams == null || queryParams.isEmpty()) {
                continue;
            }

            Key key = new Key(execution.contextId(), execution.fingerprint());
            NPlusOneAccumulator acc =  groups.computeIfAbsent(key, k -> new NPlusOneAccumulator());

            boolean hasParam = false;
            for(List<QueryParam> list : queryParams) {
                if (list == null || list.isEmpty()) continue;

                hasParam = true;

                List<String> values = new ArrayList<>();
                for(QueryParam queryParam : list) {
                     values.add(queryParam == null ? "null" : String.valueOf(queryParam.value()));
                }

                String paramKey = String.join("|", values);
                acc.addParamKey(paramKey);
            }
            if (hasParam) {
                acc.incrementCount();
                acc.addSourceLocation(execution.sourceLocation());
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
        private Map<String, Integer> sourceLocationCounts = new HashMap<>();

        public void incrementCount() {
            this.count++;
        }

        public void addParamKey(String key) {
            distinctParamKeys.add(key);
        }

        public void addSourceLocation(String sourceLocation) {
            if (sourceLocation == null || sourceLocation.isBlank()) {
                return;
            }
            sourceLocationCounts.merge(sourceLocation, 1, Integer::sum);
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
        private final Map<String, Integer> sourceLocationCounts = new HashMap<>();

        R09Summary(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        void add(int count, int distinctParams, Map<String, Integer> callSites) {
            this.requestsAffected++;
            this.maxCount = Math.max(maxCount, count);
            this.maxDistinctParams = Math.max(maxDistinctParams, distinctParams);
            callSites.forEach((sourceLocation, calls) -> sourceLocationCounts.merge(sourceLocation, calls, Integer::sum));
        }

        Finding toFinding() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requestsAffected", requestsAffected);
            details.put("maxCount", maxCount);
            details.put("maxDistinctParams", maxDistinctParams);

            topSourceLocation().ifPresent(sourceLocation -> details.put("sourceLocation", sourceLocation));

            return new Finding(
                    ID,
                    SEVERITY,
                    "Possible N+1 query detected",
                    "Batch this query using IN (...) or fetch the association eagerly where appropriate",
                    null,
                    null,
                    fingerprint,
                    details
            );
        }

        private Optional<String> topSourceLocation() {
            return sourceLocationCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);
        }
    }
}
