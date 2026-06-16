package org.example.storage;

import org.example.model.QueryExecution;

public class ConsoleExecutionSink implements ExecutionSink{

    @Override
    public void accept(QueryExecution execution) {
        System.out.println(execution);
    }
}
