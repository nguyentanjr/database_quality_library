package org.example.storage;

import org.example.fingerprint.Fingerprinter;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.model.StatsAccumulator;
import org.example.rules.config.SlowQueryConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class InMemoryExecutionSink implements ExecutionSink{

    private final int ringCapacity;
    private final QueryExecution[] ring;
    private final LongAdder totalQuery = new LongAdder();
    private final LongAdder skipQuery = new LongAdder();
    private final AtomicLong seq = new AtomicLong();
    private final Fingerprinter fingerprinter;
    private final ConcurrentHashMap<String, StatsAccumulator> stats = new ConcurrentHashMap<>();
    private final int maxFingerprints;
    private final LongAdder slowQueriesCount = new LongAdder();
    private final SlowQueryConfig slowQueryConfig;

    public InMemoryExecutionSink(int ringCapacity,Fingerprinter fingerprinter, int maxFingerprints, SlowQueryConfig config) {
        this.ringCapacity = ringCapacity;
        this.ring = new QueryExecution[ringCapacity];
        this.fingerprinter = fingerprinter;
        this.maxFingerprints = maxFingerprints;
        this.slowQueryConfig = config;
    }

    @Override
    public void accept(QueryExecution execution) {
        totalQuery.increment();

        long current = seq.getAndIncrement();
        int index = (int) (current % ringCapacity);
        ring[index] = execution;

        if (execution.durationMs() >= slowQueryConfig.thresholdMs()) {
            slowQueriesCount.increment();
        }

        String fingerprint = execution.fingerprint();

        StatsAccumulator statsAccumulator = stats.get(fingerprint);
        if (statsAccumulator == null) {
            if (stats.size() >= maxFingerprints) {
                skipQuery.increment();
                return;
            }
            statsAccumulator = stats.computeIfAbsent(
                    fingerprint,
                    k -> StatsAccumulator.parseFrom(k)
            );
        }

        statsAccumulator.recordHit();
    }

    public long slowQueryCount() {
        return slowQueriesCount.longValue();
    }

    public long totalQuery() {
        return this.totalQuery.longValue();
    }

    public long totalSkipQuery() {
        return this.skipQuery.longValue();
    }

    public List<QueryExecution> recentExecutions() {
        List<QueryExecution> result = new ArrayList<>();
        for(QueryExecution query : ring) {
            if(query != null)
            result.add(query);
        }
        return result;
    }

    public List<QueryStats> stats() {
        List<QueryStats> result = new ArrayList<>();
        for (var entry : stats.entrySet()) {
            StatsAccumulator acc = entry.getValue();
            result.add(new QueryStats(
                    entry.getKey(),
                    acc.getCount().sum(),
                    Set.copyOf(acc.getTables()),
                    Set.copyOf(acc.getColumns()),
                    acc.isSelect()));
        }
        return result;
    }

    public

}
