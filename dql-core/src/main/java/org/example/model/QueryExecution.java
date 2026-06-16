package org.example.model;

import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import java.time.Instant;
import java.util.List;

public record QueryExecution(
        String sqlRaw,
        long durationMs,
        Instant timestamp,
        String unitOfWorkId,
        String fingerprint,
        List<Object> params,
        String stackTrace
) {

}