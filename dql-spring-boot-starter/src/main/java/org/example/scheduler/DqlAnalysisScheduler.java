package org.example.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.analyzer.RuleEngine;
import org.example.model.AiContext;
import org.example.model.Finding;
import org.example.model.QualityReport;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.report.AiContextExporter;
import org.example.report.JsonReporter;
import org.example.runtime.DqlRuntimeControl;
import org.example.rules.AnalysisContext;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DqlAnalysisScheduler {

    private static final Logger log = LoggerFactory.getLogger(DqlAnalysisScheduler.class);

    private final ExecutionSink executionSink;
    private final SchemaSnapshotProvider provider;
    private final RuleEngine ruleEngine;
    private final JsonReporter jsonReporter;
    private final AiContextExporter aiContextExporter;
    private final DqlRuntimeControl runtimeControl;
    private final long delaySeconds;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("dql-analysis");
                thread.setDaemon(true);
                return thread;
            });

    private volatile QualityReport latestReport;
    private volatile AiContext latestAiContext;
    private volatile Instant lastRun;

    private long lastProcessedSeq = -1L;

    public DqlAnalysisScheduler(
            ExecutionSink executionSink,
            SchemaSnapshotProvider provider,
            RuleEngine ruleEngine,
            JsonReporter jsonReporter,
            AiContextExporter aiContextExporter,
            DqlRuntimeControl runtimeControl,
            long delaySeconds
    ) {
        if (delaySeconds <= 0) {
            throw new IllegalArgumentException("delaySeconds must be greater than 0");
        }

        this.executionSink = executionSink;
        this.provider = provider;
        this.ruleEngine = ruleEngine;
        this.jsonReporter = jsonReporter;
        this.aiContextExporter = aiContextExporter;
        this.runtimeControl = runtimeControl;
        this.delaySeconds = delaySeconds;
    }

    @PostConstruct
    public void start() {
        executor.scheduleWithFixedDelay(
                this::safeAnalyze,
                0,
                delaySeconds,
                TimeUnit.SECONDS
        );

        log.info("DQL analysis scheduler started with delaySeconds={}", delaySeconds);
    }

    private void safeAnalyze() {
        try {
            analyzeIfNeeded();
        } catch (Exception e) {
            log.error("DQL analysis failed at seq={}", lastProcessedSeq, e);
        }
    }

    private void analyzeIfNeeded() throws Exception {
        if (!runtimeControl.isEnabled()) {
            return;
        }

        long currentSeq = executionSink.currentSeq();
        if (lastRun != null && currentSeq == lastProcessedSeq) {
            return;
        }

        SchemaSnapshot snapshot = provider.get();
        List<QueryStats> stats = executionSink.stats();
        List<QueryExecution> recent = executionSink.recentExecutions();

        AnalysisContext context = new AnalysisContext(snapshot, stats, recent);

        long analysisStartNanos = System.nanoTime();
        List<Finding> findings = ruleEngine.run(context);
        long analysisMicros = (System.nanoTime() - analysisStartNanos) / 1_000;

        QualityReport report = jsonReporter.buildReport(
                findings,
                snapshot.tables().size()
        );

        AiContext aiContext = aiContextExporter.export(
                snapshot,
                findings,
                stats,
                recent
        );

        latestReport = report;
        latestAiContext = aiContext;
        lastRun = Instant.now();
        lastProcessedSeq = currentSeq;

        log.info(
                "DQL analysis: ruleEngine={} us, fingerprints={}, recentExec={}, findings={}, score={}",
                analysisMicros,
                stats.size(),
                recent.size(),
                findings.size(),
                report.score()
        );
    }

    public QualityReport latestReport() {
        return latestReport;
    }

    public AiContext latestAiContext() {
        return latestAiContext;
    }

    public Instant lastRun() {
        return lastRun;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        log.info("DQL analysis scheduler stopped");
    }
}
