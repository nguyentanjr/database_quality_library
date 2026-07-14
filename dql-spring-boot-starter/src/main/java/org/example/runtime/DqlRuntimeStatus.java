package org.example.runtime;

import java.time.Instant;

public record DqlRuntimeStatus(boolean enabled, Instant changedAt) {
}
