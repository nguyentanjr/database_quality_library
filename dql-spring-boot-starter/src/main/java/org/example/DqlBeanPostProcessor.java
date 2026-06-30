package org.example;

import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.example.context.ThreadLocalContextProvider;
import org.example.fingerprint.Fingerprinter;
import org.example.model.QueryContext;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.storage.ExecutionSink;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class DqlBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<ExecutionSink> sinkObjectProvider;

    private final Fingerprinter fingerprinter;
    public DqlBeanPostProcessor(ObjectProvider<ExecutionSink> sinkObjectProvider, Fingerprinter fingerprinter) {
        this.sinkObjectProvider = sinkObjectProvider;
        this.fingerprinter = fingerprinter;
    }

    //TODO:  stackTrace overhead
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            return ProxyDataSourceBuilder.create(dataSource)
                    .afterQuery((execInfo, queryInfoList) -> {
                        ExecutionSink sink = sinkObjectProvider.getObject();
                        QueryContext context = ThreadLocalContextProvider.current();

                        String contextId = context != null ? context.id() : null;
                        for(QueryInfo queryInfo : queryInfoList) {
                            //String stackTrace = Arrays.toString(Thread.currentThread().getStackTrace());

                            String rawSql = queryInfo.getQuery();
                            if (!shouldCapture(rawSql)) {
                                continue;
                            }

                            long duration = execInfo.getElapsedTime();
                            String fingerprint = fingerprinter.fingerprint(rawSql);
                            List<List<QueryParam>> paramSets = ParameterSetMapper.toParamSets(queryInfo.getParametersList());
                            QueryExecution queryExecution = new QueryExecution(
                                    rawSql,
                                    duration,
                                    Instant.now(),
                                    contextId,
                                    fingerprint,
                                    paramSets,
                                    null
                            );
                            sink.accept(queryExecution);
                        }
                    })
                    .build();
        }
        return bean;
    }
    private boolean shouldCapture(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }

        String s = sql.strip().toLowerCase();

        return s.startsWith("select ");
    }
}
