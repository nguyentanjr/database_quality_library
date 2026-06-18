package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dql")
public class DqlProperties {

    private boolean enabled = true;

    private final SlowQuery slowQuery = new SlowQuery();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SlowQuery getSlowQuery() {
        return slowQuery;
    }

    public static class SlowQuery {

        private long thresholdMs = 5000;

        public long getThresholdMs() {
            return thresholdMs;
        }

        public void setThresholdMs(long thresholdMs) {
            this.thresholdMs = thresholdMs;
        }
    }
}