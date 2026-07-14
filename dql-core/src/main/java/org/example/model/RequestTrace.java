package org.example.model;

import java.time.Instant;
import java.util.List;

public record RequestTrace(
        String contextId,
        String contextName,
        Instant startedAt,
        Instant endedAt,
        int queryCount,
        long totalSqlMs,
        int repeatedGroups,
        int slowQueries,
        List<String> riskBadges,
        List<RequestTraceQueryGroup> queryGroups,
        List<RequestTraceEvent> events
) {
}
