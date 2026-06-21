package org.example;


import org.example.config.DqlProperties;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.rules.Rule;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.R05_SlowQueryRule;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@EnableConfigurationProperties(DqlProperties.class)
@ConditionalOnProperty(prefix = "dql", name = "enabled", havingValue = "true")
public class DqlAutoConfiguration {

    @Bean
    public Fingerprinter fingerprinter() {
        return new RegexFingerprint();
    }

    @Bean
    public ExecutionSink executionSink() {
        return new InMemoryExecutionSink(100,fingerprinter(),1999);
    }

    @Bean
    public static DqlBeanPostProcessor dqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinks, Fingerprinter fingerprinter) {
        return new DqlBeanPostProcessor(sinks, fingerprinter);
    }

    @Bean
    public Rule slowQueryRule(DqlProperties properties) {
        return new R05_SlowQueryRule(
                new SlowQueryConfig(
                        properties.getSlowQuery().getThresholdMs()
                )
        );
    }





}
