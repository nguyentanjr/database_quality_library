package org.example.model;

import java.time.Instant;
import java.util.Set;

public record CollectedQuery(
        String fingerprint,
        long count,
        double avgMs,
        long minMs,
        long maxMs,
        Set<String> tables,
        boolean isSelect,
        String topSourceLocation,
        Instant lastSeen
) {
    public static CollectedQuery from(QueryStats stats) {
        return new CollectedQuery(
                stats.fingerprint(),
                stats.count(),
                stats.avgMs(),
                stats.minMs(),
                stats.maxMs(),
                stats.tables(),
                stats.select(),
                stats.topSourceLocation(),
                stats.lastSeen()
        );
    }
}
