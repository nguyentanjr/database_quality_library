package org.example.jdbc;

import org.example.callsite.SourceLocationConfig;
import org.example.explain.ExplainAdapter;


public class DqlJdbcConfig {

    private int ringCapacity = 5000;
    private int maxFingerprints = 2000;
    private long slowQueryThresholdMs = 5000;
    private int nPlusOneMinCount = 5;
    private int nPlusOneMinDistinctParams = 3;
    private int missingIndexThresholdCount = 10;
    private long fullScanMinEstimatedRows = 10_000;
    private int fullScanMaxExplainCandidates = 10;
    private long analysisDelaySeconds = 0;
    private SourceLocationConfig sourceLocation = SourceLocationConfig.defaults();
    private ExplainAdapter explainAdapter;

    public static DqlJdbcConfig defaults() {
        return new DqlJdbcConfig();
    }

    public int ringCapacity() {
        return ringCapacity;
    }

    public DqlJdbcConfig ringCapacity(int value) {
        this.ringCapacity = value;
        return this;
    }

    public int maxFingerprints() {
        return maxFingerprints;
    }

    public DqlJdbcConfig maxFingerprints(int value) {
        this.maxFingerprints = value;
        return this;
    }

    public long slowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public DqlJdbcConfig slowQueryThresholdMs(long value) {
        this.slowQueryThresholdMs = value;
        return this;
    }

    public int nPlusOneMinCount() {
        return nPlusOneMinCount;
    }

    public DqlJdbcConfig nPlusOneMinCount(int value) {
        this.nPlusOneMinCount = value;
        return this;
    }

    public int nPlusOneMinDistinctParams() {
        return nPlusOneMinDistinctParams;
    }

    public DqlJdbcConfig nPlusOneMinDistinctParams(int value) {
        this.nPlusOneMinDistinctParams = value;
        return this;
    }

    public int missingIndexThresholdCount() {
        return missingIndexThresholdCount;
    }

    public DqlJdbcConfig missingIndexThresholdCount(int value) {
        this.missingIndexThresholdCount = value;
        return this;
    }

    public long fullScanMinEstimatedRows() {
        return fullScanMinEstimatedRows;
    }

    public DqlJdbcConfig fullScanMinEstimatedRows(long value) {
        this.fullScanMinEstimatedRows = value;
        return this;
    }

    public int fullScanMaxExplainCandidates() {
        return fullScanMaxExplainCandidates;
    }

    public DqlJdbcConfig fullScanMaxExplainCandidates(int value) {
        this.fullScanMaxExplainCandidates = value;
        return this;
    }

    public long analysisDelaySeconds() {
        return analysisDelaySeconds;
    }

    public DqlJdbcConfig analysisDelaySeconds(long value) {
        this.analysisDelaySeconds = value;
        return this;
    }

    public SourceLocationConfig sourceLocation() {
        return sourceLocation;
    }

    public DqlJdbcConfig sourceLocation(SourceLocationConfig value) {
        this.sourceLocation = value == null ? SourceLocationConfig.defaults() : value;
        return this;
    }

    public ExplainAdapter explainAdapter() {
        return explainAdapter;
    }

    public DqlJdbcConfig explainAdapter(ExplainAdapter value) {
        this.explainAdapter = value;
        return this;
    }
}
