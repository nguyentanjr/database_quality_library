package org.example;


import org.example.adapter.PostgresExplainAdapter;
import org.example.analyzer.RuleEngine;
import org.example.config.DqlProperties;
import org.example.controller.DqlReportController;
import org.example.controller.MetricController;
import org.example.explain.ExplainAdapter;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;
import org.example.rules.config.MissingIndexConfig;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.NullableRiskConfig;
import org.example.rules.config.SlowQueryConfig;
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
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;


@AutoConfiguration
@EnableConfigurationProperties(DqlProperties.class)
@ConditionalOnProperty(
        prefix = "dql",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DqlAutoConfiguration {

    @Bean
    public SchemaSnapshotProvider schemaSnapshotProvider(DataSource dataSource) {
        return new SchemaSnapshotProvider(dataSource);
    }

    @Bean
    public Fingerprinter fingerprinter() {
        return new RegexFingerprint();
    }

    @Bean
    public ExecutionSink executionSink(Fingerprinter fingerprinter, DqlProperties properties) {
        return new InMemoryExecutionSink(
                100,
                fingerprinter,
                1999,
                new SlowQueryConfig(properties.getSlowQueries().getThresholdMs())
        );
    }

    @Bean
    public ExplainAdapter explainAdapter(DataSource dataSource) {
        return new PostgresExplainAdapter(dataSource);
    }

    @Bean
    public static DqlBeanPostProcessor dqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinks, Fingerprinter fingerprinter) {
        return new DqlBeanPostProcessor(sinks, fingerprinter);
    }

    @Bean
    public R01_MissingPrimaryKeyRule missingPrimaryKeyRule() {
        return new R01_MissingPrimaryKeyRule();
    }

    @Bean
    public R02_SuspiciousTypeRule suspiciousTypeRule() {
        return new R02_SuspiciousTypeRule();
    }

    @Bean
    public R03_NullableRiskRule nullableRiskRule() {
        return new R03_NullableRiskRule(NullableRiskConfig.defaults());
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
    public R10_FullScanTableRule fullScanTableRule(ExplainAdapter explainAdapter) {
        return new R10_FullScanTableRule(
                explainAdapter,
                FullScanConfig.defaults()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(List<Rule> rules) {
        return new RuleEngine(rules);
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
    public DqlReportController dqlReportController(
            ExecutionSink executionSink,
            RuleEngine ruleEngine,
            SchemaSnapshotProvider schemaSnapshotProvider
    ) {
        return new DqlReportController(executionSink, ruleEngine, schemaSnapshotProvider);
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
    
}

