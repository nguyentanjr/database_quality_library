package org.example;

import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.example.callsite.SourceLocationConfig;
import org.example.callsite.SourceLocationResolver;
import org.example.context.ThreadLocalContextProvider;
import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.SqlVerbs;
import org.example.model.QueryContext;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.runtime.DqlRuntimeControl;
import org.example.storage.ExecutionSink;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

public class DqlBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<ExecutionSink> sinkObjectProvider;

    private final Fingerprinter fingerprinter;
    private final SourceLocationConfig sourceLocationConfig;
    private final DqlRuntimeControl runtimeControl;

    public DqlBeanPostProcessor(
            ObjectProvider<ExecutionSink> sinkObjectProvider,
            Fingerprinter fingerprinter,
            SourceLocationConfig sourceLocationConfig,
            DqlRuntimeControl runtimeControl
    ) {
        this.sinkObjectProvider = sinkObjectProvider;
        this.fingerprinter = fingerprinter;
        this.sourceLocationConfig = sourceLocationConfig;
        this.runtimeControl = runtimeControl;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            return ProxyDataSourceBuilder.create(dataSource)
                    .afterQuery((execInfo, queryInfoList) -> {
                        if (!runtimeControl.isEnabled()) {
                            return;
                        }

                        ExecutionSink sink = sinkObjectProvider.getObject();
                        QueryContext context = ThreadLocalContextProvider.current();

                        String contextId = context != null ? context.id() : null;
                        String contextName = context != null ? context.name() : null;
                        String sourceLocation = null;
                        boolean sourceLocationResolved = false;
                        for(QueryInfo queryInfo : queryInfoList) {
                            String rawSql = queryInfo.getQuery();
                            String verb = SqlVerbs.mainVerb(rawSql);
                            if (verb == null) {
                                continue;
                            }

                            long duration = execInfo.getElapsedTime();
                            String fingerprint = fingerprinter.fingerprint(rawSql);
                            boolean isSelect = "select".equals(verb);
                            List<List<QueryParam>> paramSets = ParameterSetMapper.toParamSets(queryInfo.getParametersList());
                            if (!sourceLocationResolved) {
                                sourceLocation = SourceLocationResolver.resolve(sourceLocationConfig);
                                sourceLocationResolved = true;
                            }
                            QueryExecution queryExecution = new QueryExecution(
                                    rawSql,
                                    duration,
                                    Instant.now(),
                                    contextId,
                                    contextName,
                                    fingerprint,
                                    isSelect,
                                    paramSets,
                                    sourceLocation
                            );
                            sink.accept(queryExecution);
                        }
                    })
                    .build();
        }
        return bean;
    }
}
