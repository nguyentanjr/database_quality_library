package org.example.scheduler;

import org.example.analyzer.RuleEngine;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DqlAnalysisScheduler {

    private final ExecutionSink executionSink;

    private final SchemaSnapshotProvider provider;

    private final RuleEngine ruleEngine;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r);
                thread.setName("dql-analysis");
                thread.setDaemon(true);
                return thread;
            });

    private long lastProcessedSeq;

    private volatile
}
