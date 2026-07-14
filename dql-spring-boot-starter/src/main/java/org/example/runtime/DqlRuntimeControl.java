package org.example.runtime;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class DqlRuntimeControl {

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private volatile Instant changedAt = Instant.now();

    public boolean isEnabled() {
        return enabled.get();
    }

    public DqlRuntimeStatus status() {
        return new DqlRuntimeStatus(enabled.get(), changedAt);
    }

    public DqlRuntimeStatus enable() {
        setEnabled(true);
        return status();
    }

    public DqlRuntimeStatus disable() {
        setEnabled(false);
        return status();
    }

    private void setEnabled(boolean value) {
        if (enabled.getAndSet(value) != value) {
            changedAt = Instant.now();
        }
    }
}
