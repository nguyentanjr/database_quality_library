package org.example;

import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.example.model.QueryExecution;
import org.example.storage.ExecutionSink;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;

public class DqlBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<ExecutionSink> sinkObjectProvider;

    public DqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinkObjectProvider) {
        this.sinkObjectProvider = sinkObjectProvider;
    }

    //TODO: parameterList + stackTrace overhead
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            return ProxyDataSourceBuilder.create(dataSource)
                    .afterQuery((execInfo, queryInfoList) -> {
                        ExecutionSink sink = sinkObjectProvider.getObject();
                        for(QueryInfo queryInfo : queryInfoList) {
                            String stackTrace = Arrays.toString(Thread.currentThread().getStackTrace());
                            String rawSql = queryInfo.getQuery();
                            long duration = execInfo.getElapsedTime();
                            QueryExecution queryExecution = new QueryExecution(
                                    rawSql,
                                    duration,
                                    Instant.now(),
                                    null,
                                    null,
                                    queryInfo.getParametersList(),
                                    stackTrace
                            );
                            sink.accept(queryExecution);
                        }
                    })
                    .build();
        }
        return bean;
    }
}
