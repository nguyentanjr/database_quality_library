package org.example.model;

import java.time.Instant;
import java.util.List;

public record QueryExecution(
        String sqlRaw,
        long durationMs,
        Instant timestamp,
        String contextId,
        String contextName,
        String fingerprint,
        boolean select,
        List<List<QueryParam>> paramSets,
        String sourceLocation
) {

    public QueryExecution(
            String sqlRaw,
            long durationMs,
            Instant timestamp,
            String contextId,
            String fingerprint,
            boolean select,
            List<List<QueryParam>> paramSets,
            String sourceLocation
    ) {
        this(sqlRaw, durationMs, timestamp, contextId, null, fingerprint, select, paramSets, sourceLocation);
    }
}
