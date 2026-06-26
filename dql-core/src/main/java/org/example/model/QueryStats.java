package org.example.model;

import java.util.Set;

public record QueryStats(
        String fingerprint,
        long count,
        Set<String> tables,
        Set<String> columns,
        boolean select
) {

    public QueryStats(String fingerprint, long count, Set<String> tables, Set<String> columns) {
        this(fingerprint, count, tables, columns, false);
    }
}
