package org.example.storage;

import org.example.model.QueryExecution;
import org.example.model.QueryStats;

import java.util.List;

public interface ExecutionSink {

    void accept(QueryExecution execution);

    List<QueryStats> stats();

    List<QueryExecution> recentExecutions();

    long slowQueryCount();

    long totalQuery();

    long totalSkipQuery();
}
