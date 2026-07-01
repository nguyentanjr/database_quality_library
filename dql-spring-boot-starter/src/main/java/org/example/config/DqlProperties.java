package org.example.config;

import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.SlowQueryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dql")
public class DqlProperties {

    private boolean enabled = true;

    private SlowQueries slowQueries = new SlowQueries();

    private NPlusOne nPlusOne = new NPlusOne();

    private MissingIndex missingIndex = new MissingIndex();

    private Endpoints endpoints = new Endpoints();

    private Analysis analysis = new Analysis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SlowQueries getSlowQueries() {
        return slowQueries;
    }

    public void setSlowQueries(SlowQueries slowQueries) {
        this.slowQueries = slowQueries;
    }

    public NPlusOne getNPlusOne() {
        return nPlusOne;
    }

    public void setNPlusOne(NPlusOne nPlusOne) {
        this.nPlusOne = nPlusOne;
    }

    public MissingIndex getMissingIndex() {
        return missingIndex;
    }

    public void setMissingIndex(MissingIndex missingIndex) {
        this.missingIndex = missingIndex;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public static class SlowQueries {
        private long thresholdMs = SlowQueryConfig.defaults().thresholdMs();

        public long getThresholdMs() {
            return thresholdMs;
        }

        public void setThresholdMs(long thresholdMs) {
            this.thresholdMs = thresholdMs;
        }
    }

    public static class NPlusOne {
        private int minCount = NPlusOneConfig.defaults().minCount();
        private int minDistinctParams = NPlusOneConfig.defaults().minDistinctParams();

        public int getMinCount() {
            return minCount;
        }

        public void setMinCount(int minCount) {
            this.minCount = minCount;
        }

        public int getMinDistinctParams() {
            return minDistinctParams;
        }

        public void setMinDistinctParams(int minDistinctParams) {
            this.minDistinctParams = minDistinctParams;
        }
    }

    public static class MissingIndex {
        private int thresholdCount = 10;

        public int getThresholdCount() {
            return thresholdCount;
        }

        public void setThresholdCount(int thresholdCount) {
            this.thresholdCount = thresholdCount;
        }
    }

    public static class Endpoints {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Analysis {
        private boolean enabled = true;
        private long delaySeconds = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDelaySeconds() {
            return delaySeconds;
        }

        public void setDelaySeconds(long delaySeconds) {
            this.delaySeconds = delaySeconds;
        }
    }
}
