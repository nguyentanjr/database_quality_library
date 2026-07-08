package org.example.callsite;

import java.util.List;

public record SourceLocationConfig(
        boolean enabled,
        List<String> applicationPrefixes,
        List<String> extraInternalPrefixes,
        int maxDepth
) {
    public static SourceLocationConfig defaults() {
        return new SourceLocationConfig(true, List.of(), List.of(), 256);
    }

    public SourceLocationConfig {
        applicationPrefixes = applicationPrefixes == null ? List.of() : List.copyOf(applicationPrefixes);
        extraInternalPrefixes = extraInternalPrefixes == null ? List.of() : List.copyOf(extraInternalPrefixes);
        maxDepth = maxDepth <= 0 ? 256 : maxDepth;
    }
}
