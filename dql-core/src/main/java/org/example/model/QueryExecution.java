package org.example.model;

import java.time.Instant;
import java.util.List;

public record QueryExecution(
        String sqlRaw,
        long durationMs,
        Instant timestamp,
        String unitOfWorkId,
        String fingerprint,
        List<List<QueryParam>> paramSets,
        String stackTrace
) {


}