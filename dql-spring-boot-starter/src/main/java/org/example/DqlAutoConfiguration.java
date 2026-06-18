package org.example;


import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.storage.ExecutionSink;
import org.example.storage.InMemoryExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
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
    public static DqlBeanPostProcessor dqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinks) {
        return new DqlBeanPostProcessor(sinks);
    }






}
