package org.example;


import org.example.config.DqlProperties;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.rules.Rule;
import org.example.rules.config.NPlusOneConfig;
import org.example.rules.config.SlowQueryConfig;
import org.example.rules.impl.R05_SlowQueryRule;
import org.example.rules.impl.R09_NPlusOneRule;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


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
    public Fingerprinter fingerprinter() {
        return new RegexFingerprint();
    }

    @Bean
    public ExecutionSink executionSink(Fingerprinter fingerprinter) {
        return new InMemoryExecutionSink(100, fingerprinter, 1999);
    }

    @Bean
    public static DqlBeanPostProcessor dqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinks, Fingerprinter fingerprinter) {
        return new DqlBeanPostProcessor(sinks, fingerprinter);
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
    public R09_NPlusOneRule nPlusOneRule(DqlProperties properties) {
        return new R09_NPlusOneRule(
                new NPlusOneConfig(
                        properties.getNPlusOne().getMinCount(),
                        properties.getNPlusOne().getMinDistinctParams()
                )
        );
    }

    
}

