package org.example.model;

import java.util.List;

public record RequestTraceQueryGroup(
        String fingerprint,
        int count,
        int distinctParamCount,
        double avgMs,
        long totalMs,
        String topSourceLocation,
        List<String> flags
) {
}
