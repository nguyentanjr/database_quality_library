package org.example;


import org.example.adapter.MariaDbExplainAdapter;
import org.example.adapter.MySqlExplainAdapter;
import org.example.adapter.PostgresExplainAdapter;
import org.example.adapter.SqlServerExplainAdapter;
import org.example.ai.AiReviewer;
import org.example.ai.HttpAiReviewer;
import org.example.analyzer.RuleEngine;
import org.example.callsite.SourceLocationConfig;
import org.example.config.DqlProperties;
import org.example.config.WebConfig;
import org.example.controller.AiReviewController;
import org.example.controller.DqlReportController;
import org.example.controller.DqlTransparencyController;
import org.example.controller.MetricController;
import org.example.explain.ExplainAdapter;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.report.AiContextExporter;
import org.example.report.JsonReporter;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.NullableRiskConfig;
import org.example.rules.config.ScoreConfig;
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
import org.example.scheduler.DqlAnalysisScheduler;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@AutoConfiguration
@EnableConfigurationProperties(DqlProperties.class)
@Import(WebConfig.class)
@ConditionalOnProperty(
        prefix = "dql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SchemaSnapshotProvider schemaSnapshotProvider(DataSource dataSource) {
        return new SchemaSnapshotProvider(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(Fingerprinter.class)
    public Fingerprinter fingerprinter() {
        return new RegexFingerprint();
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionSink.class)
    public ExecutionSink executionSink(Fingerprinter fingerprinter, DqlProperties properties) {
        return new InMemoryExecutionSink(
                properties.getSink().getRingCapacity(),
                fingerprinter,
                properties.getSink().getMaxFingerprints(),
                new SlowQueryConfig(properties.getSlowQueries().getThresholdMs())
        );
    }

    @Bean
    @ConditionalOnMissingBean(ExplainAdapter.class)
    public ExplainAdapter explainAdapter(DataSource dataSource) {
        // Auto-detect: đổi DB (URL) không cần sửa code/config. Vẫn override được (CoMB).
        String product = detectDatabaseProduct(dataSource);
        if (product != null && product.contains("mariadb")) {
            return new MariaDbExplainAdapter(dataSource);
        }
        if (product != null && product.contains("mysql")) {
            return new MySqlExplainAdapter(dataSource);
        }
        if (product != null && product.contains("sql server")) {
            return new SqlServerExplainAdapter(dataSource);
        }
        return new PostgresExplainAdapter(dataSource); // default
    }

    private static String detectDatabaseProduct(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            // Detect fail (vd DB chưa sẵn lúc khởi động) -> dùng default, KHÔNG chặn app start.
            return null;
        }
    }

    @Bean
    public static DqlBeanPostProcessor dqlBeanPostProcessor(
            ObjectProvider<ExecutionSink> sinks,
            Fingerprinter fingerprinter,
            DqlProperties properties
    ) {
        DqlProperties.SourceLocation sourceLocation = properties.getSourceLocation();
        return new DqlBeanPostProcessor(
                sinks,
                fingerprinter,
                new SourceLocationConfig(
                        sourceLocation.isEnabled(),
                        sourceLocation.getApplicationPrefixes(),
                        sourceLocation.getExtraInternalPrefixes(),
                        sourceLocation.getMaxDepth()
                )
        );
    }

    @Bean
    public R01_MissingPrimaryKeyRule missingPrimaryKeyRule() {
        return new R01_MissingPrimaryKeyRule();
    }

    @Bean
    public R02_SuspiciousTypeRule suspiciousTypeRule(DqlProperties properties) {
        List<DqlProperties.SuspiciousType.TypeRule> propRules = properties.getSuspiciousType().getRules();
        SuspiciousTypeConfig config = propRules.isEmpty()
                ? SuspiciousTypeConfig.defaults()
                : new SuspiciousTypeConfig(
                propRules.stream()
                        .map(r -> new SuspiciousTypeConfig.TypeRule(
                                r.getColumns(),
                                r.getBadTypes(),
                                r.getSeverity(),
                                r.getMessage(),
                                r.getSuggestion()
                        ))
                        .toList()
        );
        return new R02_SuspiciousTypeRule(config);
    }

    @Bean
    public R03_NullableRiskRule nullableRiskRule(DqlProperties properties) {
        return new R03_NullableRiskRule(
                new NullableRiskConfig(properties.getNullableRisk().getColumnNameRules())
        );
    }

    @Bean
    public R04_SelectStarRule selectStarRule() {
        return new R04_SelectStarRule();
    }

    @Bean
    public R05_SlowQueryRule slowQueryRule(DqlProperties properties) {
        return new R05_SlowQueryRule(
                new SlowQueryConfig(
                        properties.getSlowQueries().getThresholdMs()
                )
        );
    }

    @Bean
    public R06_UnindexedFKRule unindexedFkRule() {
        return new R06_UnindexedFKRule();
    }

    @Bean
    public R07_MissingIndexRule missingIndexRule(DqlProperties properties) {
        return new R07_MissingIndexRule(
                new MissingIndexConfig(
                        properties.getMissingIndex().getThresholdCount()
                )
        );
    }

    @Bean
    public R08_UnusedIndexRule unusedIndexRule() {
        return new R08_UnusedIndexRule();
    }

    @Bean
    public R09_NPlusOneRule nPlusOneRule(DqlProperties properties) {
        return new R09_NPlusOneRule(
                new NPlusOneConfig(
                        properties.getNPlusOne().getMinCount(),
                        properties.getNPlusOne().getMinDistinctParams()
                )
        );
    }

    @Bean
    public R10_FullScanTableRule fullScanTableRule(ExplainAdapter explainAdapter, DqlProperties properties) {
        return new R10_FullScanTableRule(
                explainAdapter,
                new FullScanConfig(
                        properties.getFullScan().getMinEstimatedRows(),
                        properties.getFullScan().getMaxExplainCandidates()
                )
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(List<Rule> rules, DqlProperties properties) {
        Set<String> disabled = properties.getRules().entrySet().stream()
                .filter(entry -> Boolean.FALSE.equals(entry.getValue()))
                .map(entry -> entry.getKey().toLowerCase())
                .collect(Collectors.toSet());

        List<Rule> active = rules.stream()
                .filter(rule -> !disabled.contains(rule.id().toLowerCase()))
                .toList();

        return new RuleEngine(active);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "dql.analysis",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DqlAnalysisScheduler dqlAnalysisScheduler(
            ExecutionSink executionSink,
            SchemaSnapshotProvider schemaSnapshotProvider,
            RuleEngine ruleEngine,
            JsonReporter jsonReporter,
            AiContextExporter aiContextExporter,
            DqlProperties properties
    ) {
        return new DqlAnalysisScheduler(
                executionSink,
                schemaSnapshotProvider,
                ruleEngine,
                jsonReporter,
                aiContextExporter,
                properties.getAnalysis().getDelaySeconds()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "dql.endpoints",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    @ConditionalOnBean(DqlAnalysisScheduler.class)
    public DqlReportController dqlReportController(
            DqlAnalysisScheduler scheduler
    ) {
        return new DqlReportController(scheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "dql.endpoints",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public MetricController metricController(
            ExecutionSink executionSink,
            R09_NPlusOneRule nPlusOneRule
    ) {
        return new MetricController(executionSink, nPlusOneRule);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "dql.endpoints",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DqlTransparencyController dqlTransparencyController(
            ExecutionSink executionSink,
            SchemaSnapshotProvider schemaSnapshotProvider
    ) {
        return new DqlTransparencyController(executionSink, schemaSnapshotProvider);
    }


    @Bean
    @ConditionalOnMissingBean
    public JsonReporter jsonReporter(DqlProperties properties) {
        return new JsonReporter(new ScoreConfig(properties.getScore().getWeights()));
    }

    @Bean
    @ConditionalOnMissingBean
    public AiContextExporter aiContextExporter(DqlProperties properties) {
        return new AiContextExporter(
                properties.getAiContext().getTopFindings(),
                properties.getAiContext().getSampleQueries(),
                properties.getAiContext().getTopTables()
        );
    }

    @Bean
    @ConditionalOnMissingBean(AiReviewer.class)
    @ConditionalOnProperty(
            prefix = "dql.ai",
            name = "enabled",
            havingValue = "true"
    )
    public AiReviewer aiReviewer(DqlProperties properties) {
        DqlProperties.Ai ai = properties.getAi();
        if (ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "dql.ai.enabled=true but dql.ai.api-key is blank — set it via env var (e.g. ${DQL_AI_API_KEY})"
            );
        }
        return new HttpAiReviewer(
                ai.getEndpoint(),
                ai.getApiKey(),
                ai.getModel(),
                ai.getTimeoutMs()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DqlAnalysisScheduler.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "dql.endpoints",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public AiReviewController aiReviewController(
            DqlAnalysisScheduler scheduler,
            ObjectProvider<AiReviewer> aiReviewer
    ) {
        // Endpoint LUÔN có (web+endpoints+scheduler). AI tắt -> AiReviewer null -> controller trả "disabled".
        return new AiReviewController(scheduler, aiReviewer.getIfAvailable());
    }


}
