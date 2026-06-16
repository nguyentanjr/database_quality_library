package org.example.storage;

import org.example.model.QueryExecution;

public interface ExecutionSink {

    void accept(QueryExecution execution);
}
