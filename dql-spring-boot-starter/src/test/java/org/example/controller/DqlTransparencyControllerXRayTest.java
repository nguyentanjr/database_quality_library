package org.example.controller;

import org.example.config.DqlProperties;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.QueryStats;
import org.example.model.RequestTrace;
import org.example.model.RequestTraceQueryGroup;
import org.example.schema.SchemaSnapshotProvider;
import org.example.storage.ExecutionSink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DqlTransparencyControllerXRayTest {

    @Test
    void groups_recent_executions_into_request_xray_without_exposing_raw_values() {
        FakeExecutionSink sink = new FakeExecutionSink();
        DqlTransparencyController controller = new DqlTransparencyController(
                sink,
                new SchemaSnapshotProvider(null),
                new DqlProperties()
        );

        Instant start = Instant.parse("2026-07-09T16:00:00Z");
        for (int i = 0; i < 5; i++) {
            sink.accept(new QueryExecution(
                    "select * from visits where pet_id = " + (100 + i),
                    10,
                    start.plusMillis(i),
                    "ctx-1",
                    "GET /api/owners",
                    "select * from visits where pet_id = ?",
                    true,
                    List.of(List.of(new QueryParam(1, 100 + i))),
                    "org.springframework.samples.petclinic.service.ClinicServiceImpl.findAllOwners(ClinicServiceImpl.java:128)"
            ));
        }

        List<RequestTrace> traces = controller.requestTraces(10, 3, 1);

        assertEquals(1, traces.size());
        RequestTrace trace = traces.getFirst();
        assertEquals("ctx-1", trace.contextId());
        assertEquals("GET /api/owners", trace.contextName());
        assertEquals(5, trace.queryCount());
        assertEquals(50, trace.totalSqlMs());
        assertEquals(3, trace.events().size());
        assertEquals(1, trace.queryGroups().size());
        assertTrue(trace.riskBadges().contains("N+1"));

        RequestTraceQueryGroup group = trace.queryGroups().getFirst();
        assertEquals(5, group.count());
        assertEquals(5, group.distinctParamCount());
        assertTrue(group.flags().contains("n-plus-one-candidate"));
        assertEquals("org.springframework.samples.petclinic.service.ClinicServiceImpl.findAllOwners(ClinicServiceImpl.java:128)", group.topSourceLocation());

        String exported = trace.toString();
        assertFalse(exported.contains("pet_id = 100"));
        assertFalse(exported.contains("100|101"));
    }

    private static final class FakeExecutionSink implements ExecutionSink {
        private final List<QueryExecution> executions = new ArrayList<>();

        @Override
        public void accept(QueryExecution execution) {
            executions.add(execution);
        }

        @Override
        public List<QueryStats> stats() {
            return List.of();
        }

        @Override
        public List<QueryExecution> recentExecutions() {
            return List.copyOf(executions);
        }

        @Override
        public long slowQueryCount() {
            return 0;
        }

        @Override
        public long totalQuery() {
            return executions.size();
        }

        @Override
        public long totalSkipQuery() {
            return 0;
        }

        @Override
        public long currentSeq() {
            return executions.size();
        }
    }
}
