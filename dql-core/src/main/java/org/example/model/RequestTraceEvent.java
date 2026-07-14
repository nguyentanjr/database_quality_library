package org.example.model;

import java.time.Instant;
import java.util.List;

public record RequestTraceEvent(
        int order,
        Instant timestamp,
        long durationMs,
        String fingerprint,
        String sourceLocation,
        List<String> flags
) {
}
