package org.example.config;

    import org.example.enums.Severity;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.NullableRiskConfig;
import org.example.rules.config.ScoreConfig;
import org.example.rules.config.SlowQueryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "dql")
public class DqlProperties {

    private boolean enabled = true;

    private SlowQueries slowQueries = new SlowQueries();

    private NPlusOne nPlusOne = new NPlusOne();

    private MissingIndex missingIndex = new MissingIndex();

    private Endpoints endpoints = new Endpoints();

    private Analysis analysis = new Analysis();

    private Sink sink = new Sink();

    private FullScan fullScan = new FullScan();

    private NullableRisk nullableRisk = new NullableRisk();

    private Ai ai = new Ai();

    private Score score = new Score();

    private SuspiciousType suspiciousType = new SuspiciousType();

    private AiContextExport aiContext = new AiContextExport();

    // dql.rules.<id>: false để tắt rule (id = Rule.id(), vd R08_UnusedIndex). Trống = bật hết.
    private Map<String, Boolean> rules = new HashMap<>();

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

    public Sink getSink() {
        return sink;
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public FullScan getFullScan() {
        return fullScan;
    }

    public void setFullScan(FullScan fullScan) {
        this.fullScan = fullScan;
    }

    public NullableRisk getNullableRisk() {
        return nullableRisk;
    }

    public void setNullableRisk(NullableRisk nullableRisk) {
        this.nullableRisk = nullableRisk;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    public SuspiciousType getSuspiciousType() {
        return suspiciousType;
    }

    public void setSuspiciousType(SuspiciousType suspiciousType) {
        this.suspiciousType = suspiciousType;
    }

    public AiContextExport getAiContext() {
        return aiContext;
    }

    public void setAiContext(AiContextExport aiContext) {
        this.aiContext = aiContext;
    }

    public Map<String, Boolean> getRules() {
        return rules;
    }

    public void setRules(Map<String, Boolean> rules) {
        this.rules = rules;
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

    public static class Sink {

        private int ringCapacity = 5000;

        private int maxFingerprints = 2000;

        public int getRingCapacity() {
            return ringCapacity;
        }

        public void setRingCapacity(int ringCapacity) {
            this.ringCapacity = ringCapacity;
        }

        public int getMaxFingerprints() {
            return maxFingerprints;
        }

        public void setMaxFingerprints(int maxFingerprints) {
            this.maxFingerprints = maxFingerprints;
        }
    }

    public static class FullScan {
        private long minEstimatedRows = FullScanConfig.defaults().minEstimatedRows();
        private int maxExplainCandidates = FullScanConfig.defaults().maxExplainCandidates();

        public long getMinEstimatedRows() {
            return minEstimatedRows;
        }

        public void setMinEstimatedRows(long minEstimatedRows) {
            this.minEstimatedRows = minEstimatedRows;
        }

        public int getMaxExplainCandidates() {
            return maxExplainCandidates;
        }

        public void setMaxExplainCandidates(int maxExplainCandidates) {
            this.maxExplainCandidates = maxExplainCandidates;
        }
    }

    public static class NullableRisk {
        private Map<Severity, List<String>> columnNameRules =
                new LinkedHashMap<>(NullableRiskConfig.defaults().columnNameRules());

        public Map<Severity, List<String>> getColumnNameRules() {
            return columnNameRules;
        }

        public void setColumnNameRules(Map<Severity, List<String>> columnNameRules) {
            this.columnNameRules = columnNameRules;
        }
    }

    public static class Ai {
        private boolean enabled = false;

        private String apiKey = "";

        private String endpoint = "https://api.openai.com/v1/chat/completions";

        private String model = "gpt-4o-mini";

        private long timeoutMs = 20_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Score {
        // Weight (điểm trừ) mỗi severity. Mutable copy để Spring override từng key.
        private Map<Severity, Integer> weights = new EnumMap<>(ScoreConfig.defaults().weights());

        public Map<Severity, Integer> getWeights() {
            return weights;
        }

        public void setWeights(Map<Severity, Integer> weights) {
            this.weights = weights;
        }
    }

    public static class SuspiciousType {
        // Trống = auto-config dùng SuspiciousTypeConfig.defaults(). Set = thay thế toàn bộ.
        private List<TypeRule> rules = new ArrayList<>();

        public List<TypeRule> getRules() {
            return rules;
        }

        public void setRules(List<TypeRule> rules) {
            this.rules = rules;
        }

        public static class TypeRule {
            private List<String> columns = new ArrayList<>();
            private Set<String> badTypes = new LinkedHashSet<>();
            private Severity severity;
            private String message;
            private String suggestion;

            public List<String> getColumns() {
                return columns;
            }

            public void setColumns(List<String> columns) {
                this.columns = columns;
            }

            public Set<String> getBadTypes() {
                return badTypes;
            }

            public void setBadTypes(Set<String> badTypes) {
                this.badTypes = badTypes;
            }

            public Severity getSeverity() {
                return severity;
            }

            public void setSeverity(Severity severity) {
                this.severity = severity;
            }

            public String getMessage() {
                return message;
            }

            public void setMessage(String message) {
                this.message = message;
            }

            public String getSuggestion() {
                return suggestion;
            }

            public void setSuggestion(String suggestion) {
                this.suggestion = suggestion;
            }
        }
    }

    public static class AiContextExport {
        private int topFindings = 5;
        private int sampleQueries = 5;
        private int topTables = 5;

        public int getTopFindings() {
            return topFindings;
        }

        public void setTopFindings(int topFindings) {
            this.topFindings = topFindings;
        }

        public int getSampleQueries() {
            return sampleQueries;
        }

        public void setSampleQueries(int sampleQueries) {
            this.sampleQueries = sampleQueries;
        }

        public int getTopTables() {
            return topTables;
        }

        public void setTopTables(int topTables) {
            this.topTables = topTables;
        }
    }
}
