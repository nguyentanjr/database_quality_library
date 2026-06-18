package org.example;


import org.example.storage.ConsoleExecutionSink;
import org.example.storage.ExecutionSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
@ConditionalOnProperty(prefix = "dql", name = "enabled", havingValue = "true")
public class DqlAutoConfiguration {

    @Bean
    public ExecutionSink executionSink() {
        return new ConsoleExecutionSink();
    }

    @Bean
    public static DqlBeanPostProcessor dqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinks) {
        return new DqlBeanPostProcessor(sinks);
    }






}
