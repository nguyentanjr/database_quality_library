package org.example.storage;

import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;

import java.util.List;

public class ConsoleExecutionSink implements ExecutionSink{

    @Override
    public void accept(QueryExecution execution) {
        Fingerprinter fingerprinter = new RegexFingerprint();
        System.out.println(fingerprinter.fingerprint(execution.sqlRaw()));
    }

    @Override
    public List<QueryStats> stats() {
        return List.of();
    }

    @Override
    public List<QueryExecution> recentExecutions() {
        return List.of();
    }

    @Override
    public long slowQueryCount() {
        return 0;
    }

    @Override
    public long totalQuery() {
        return 0;
    }

    @Override
    public long totalSkipQuery() {
        return 0;
    }

    @Override
    public long currentSeq() {
        return 0;
    }
}
