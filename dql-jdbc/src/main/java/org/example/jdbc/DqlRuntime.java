package org.example.jdbc;

import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.example.analyzer.RuleEngine;
import org.example.callsite.SourceLocationConfig;
import org.example.callsite.SourceLocationResolver;
import org.example.collector.SchemaCollector;
import org.example.context.ThreadLocalContextProvider;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.fingerprint.SqlVerbs;
import org.example.jdbc.internal.PlainParamMapper;
import org.example.model.AiContext;
import org.example.model.Finding;
import org.example.model.QualityReport;
import org.example.model.QueryContext;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.QueryStats;
import org.example.model.SchemaSnapshot;
import org.example.report.AiContextExporter;
import org.example.report.JsonReporter;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.NullableRiskConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.config.SuspiciousTypeConfig;
import org.example.rules.impl.R01_MissingPrimaryKeyRule;
import org.example.rules.impl.R02_SuspiciousTypeRule;
import org.example.rules.impl.R03_NullableRiskRule;
import org.example.rules.impl.R04_SelectStarRule;
import org.example.rules.impl.R05_SlowQueryRule;
import org.example.rules.impl.R06_UnindexedFKRule;
import org.example.rules.impl.R07_MissingIndexRule;
import org.example.rules.impl.R08_UnusedIndexRule;
import org.example.rules.impl.R09_NPlusOneRule;
import org.example.rules.impl.R10_FullScanTableRule;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class DqlRuntime implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DqlRuntime.class.getName());

    private final DataSource original;
    private final DataSource proxied;
    private final InMemoryExecutionSink sink;
    private final RuleEngine engine;
    private final JsonReporter reporter;
    private final AiContextExporter exporter;
    private final ScheduledExecutorService scheduler;

    private volatile SchemaSnapshot snapshot;
    private volatile QualityReport latestReport;
    private volatile AiContext latestAiContext;
    private volatile Instant lastRun;

    DqlRuntime(DataSource dataSource, DqlJdbcConfig config) {
        DqlJdbcConfig cfg = config == null ? DqlJdbcConfig.defaults() : config;
        this.original = dataSource;

        Fingerprinter fingerprinter = new RegexFingerprint();
        this.sink = new InMemoryExecutionSink(
                cfg.ringCapacity(),
                fingerprinter,
                cfg.maxFingerprints(),
                new SlowQueryConfig(cfg.slowQueryThresholdMs())
        );
        this.engine = new RuleEngine(buildRules(cfg));
        this.reporter = new JsonReporter();
        this.exporter = new AiContextExporter();
        this.proxied = wrap(dataSource, fingerprinter, sink, cfg.sourceLocation());

        if (cfg.analysisDelaySeconds() > 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dql-analysis");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleWithFixedDelay(
                    this::safeAnalyze,
                    cfg.analysisDelaySeconds(),
                    cfg.analysisDelaySeconds(),
                    TimeUnit.SECONDS
            );
        } else {
            this.scheduler = null;
        }
    }

    private static List<Rule> buildRules(DqlJdbcConfig cfg) {
        List<Rule> rules = new ArrayList<>(List.of(
                new R01_MissingPrimaryKeyRule(),
                new R02_SuspiciousTypeRule(SuspiciousTypeConfig.defaults()),
                new R03_NullableRiskRule(NullableRiskConfig.defaults()),
                new R04_SelectStarRule(),
                new R05_SlowQueryRule(new SlowQueryConfig(cfg.slowQueryThresholdMs())),
                new R06_UnindexedFKRule(),
                new R07_MissingIndexRule(new MissingIndexConfig(cfg.missingIndexThresholdCount())),
                new R08_UnusedIndexRule(),
                new R09_NPlusOneRule(new NPlusOneConfig(
                        cfg.nPlusOneMinCount(),
                        cfg.nPlusOneMinDistinctParams()
                ))
        ));
        // R10 cần ExplainAdapter (I/O DB-specific, impl ở starter) — plain Java
        // chỉ bật khi user tự cung cấp adapter.
        if (cfg.explainAdapter() != null) {
            rules.add(new R10_FullScanTableRule(
                    cfg.explainAdapter(),
                    new FullScanConfig(
                            cfg.fullScanMinEstimatedRows(),
                            cfg.fullScanMaxExplainCandidates()
                    )
            ));
        }
        return rules;
    }

    private static DataSource wrap(
            DataSource dataSource,
            Fingerprinter fingerprinter,
            ExecutionSink sink,
            SourceLocationConfig sourceLocationConfig
    ) {
        return ProxyDataSourceBuilder.create(dataSource)
                .afterQuery((execInfo, queryInfoList) -> {
                    // Observer must never break the observed: mọi lỗi của DQL
                    // chỉ mất telemetry, không được ném vào JDBC call của app.
                    try {
                        QueryContext context = ThreadLocalContextProvider.current();
                        String contextId = context != null ? context.id() : null;
                        String contextName = context != null ? context.name() : null;
                        String sourceLocation = null;
                        boolean sourceLocationResolved = false;

                        for (QueryInfo queryInfo : queryInfoList) {
                            String rawSql = queryInfo.getQuery();
                            String verb = SqlVerbs.mainVerb(rawSql);
                            if (verb == null) {
                                continue;
                            }
                            if (!sourceLocationResolved) {
                                sourceLocation = SourceLocationResolver.resolve(sourceLocationConfig);
                                sourceLocationResolved = true;
                            }
                            List<List<QueryParam>> paramSets =
                                    PlainParamMapper.toParamSets(queryInfo.getParametersList());
                            sink.accept(new QueryExecution(
                                    rawSql,
                                    execInfo.getElapsedTime(),
                                    Instant.now(),
                                    contextId,
                                    contextName,
                                    fingerprinter.fingerprint(rawSql),
                                    "select".equals(verb),
                                    paramSets,
                                    sourceLocation
                            ));
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "DQL capture failed (ignored)", e);
                    }
                })
                .build();
    }

    /** DataSource da duoc boc; app phai dung instance nay thay cho DataSource goc. */
    public DataSource dataSource() {
        return proxied;
    }


    public synchronized QualityReport analyzeNow() {
        SchemaSnapshot schema = collectSchemaOnce();
        List<QueryStats> stats = sink.stats();
        List<QueryExecution> recent = sink.recentExecutions();

        List<Finding> findings = engine.run(new AnalysisContext(schema, stats, recent));
        QualityReport report = reporter.buildReport(findings, schema.tables().size());
        AiContext aiContext = exporter.export(schema, findings, stats, recent);

        this.latestReport = report;
        this.latestAiContext = aiContext;
        this.lastRun = Instant.now();
        return report;
    }

    private void safeAnalyze() {
        try {
            analyzeNow();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "DQL analysis cycle failed", e);
        }
    }

    private SchemaSnapshot collectSchemaOnce() {
        SchemaSnapshot cached = snapshot;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (snapshot == null) {
                try {
                    snapshot = new SchemaCollector().collect(original);
                } catch (SQLException e) {
                    throw new IllegalStateException("DQL schema collection failed", e);
                }
            }
            return snapshot;
        }
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

    public List<QueryStats> collectedQueries() {
        return sink.stats().stream()
                .sorted(Comparator.comparingLong(QueryStats::count).reversed())
                .toList();
    }

    public SchemaSnapshot schemaSnapshot() {
        return collectSchemaOnce();
    }


    public UnitOfWork beginUnitOfWork(String name) {
        ThreadLocalContextProvider.set(new QueryContext(UUID.randomUUID().toString(), name));
        return ThreadLocalContextProvider::clear;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
