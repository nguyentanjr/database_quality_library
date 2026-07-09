package org.example.model;

import java.time.Instant;
import java.util.Set;

public record QueryStats(
        String fingerprint,
        long count,
        Set<String> tables,
        Set<String> columns,
        boolean select,
        double avgMs,
        long minMs,
        long maxMs,
        String topSourceLocation,
        Instant lastSeen
) {

    public QueryStats(String fingerprint, long count, Set<String> tables, Set<String> columns) {
        this(fingerprint, count, tables, columns, false);
    }

    public QueryStats(String fingerprint, long count, Set<String> tables, Set<String> columns, boolean select) {
        this(fingerprint, count, tables, columns, select, 0, 0, 0, null, null);
    }
}
