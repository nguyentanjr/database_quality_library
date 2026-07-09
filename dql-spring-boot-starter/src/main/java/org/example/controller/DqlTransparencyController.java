package org.example.controller;

import org.example.model.CollectedQuery;
import org.example.model.SchemaSnapshot;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/dql")
public class DqlTransparencyController {

    private static final int DEFAULT_QUERY_LIMIT = 50;
    private static final int MAX_QUERY_LIMIT = 500;

    private final ExecutionSink executionSink;
    private final SchemaSnapshotProvider schemaSnapshotProvider;

    public DqlTransparencyController(
            ExecutionSink executionSink,
            SchemaSnapshotProvider schemaSnapshotProvider
    ) {
        this.executionSink = executionSink;
        this.schemaSnapshotProvider = schemaSnapshotProvider;
    }

    @GetMapping("/collected-queries")
    public List<CollectedQuery> collectedQueries(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_QUERY_LIMIT) int limit
    ) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_QUERY_LIMIT));
        return executionSink.stats().stream()
                .map(CollectedQuery::from)
                .sorted(Comparator.comparingLong(CollectedQuery::count).reversed())
                .limit(effectiveLimit)
                .toList();
    }

    @GetMapping("/schema-snapshot")
    public SchemaSnapshot schemaSnapshot() {
        try {
            return schemaSnapshotProvider.get();
        } catch (SQLException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DQL schema snapshot is not available",
                    e
            );
        }
    }
}
