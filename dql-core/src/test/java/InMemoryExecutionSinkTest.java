import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.model.QueryExecution;
import org.example.rules.config.SlowQueryConfig;
import org.example.storage.InMemoryExecutionSink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExecutionSinkTest {

    private final Fingerprinter fingerprinter = new RegexFingerprint();

    private QueryExecution exec(String sql) {
        // Contract production: BPP tính fingerprint TRƯỚC khi accept.
        return new QueryExecution(sql, 1, Instant.now(), "ctx",
                fingerprinter.fingerprint(sql), true, List.of(), null);
    }

    @Test
    void ring_buffer_is_bounded_and_overwrites_oldest() {
        InMemoryExecutionSink sink = new InMemoryExecutionSink(
                3, fingerprinter, 1000, SlowQueryConfig.defaults());

        for (int i = 0; i < 5; i++) {
            sink.accept(exec("select * from t where id = " + i));
        }

        assertEquals(3, sink.recentExecutions().size(), "ring capacity chặn recentExecutions");
        assertEquals(5, sink.totalQuery(), "totalQuery vẫn đếm đủ mọi lượt");
    }

    @Test
    void stats_are_capped_by_max_fingerprints_and_skip_counted() {
        InMemoryExecutionSink sink = new InMemoryExecutionSink(
                1000, fingerprinter, 2, SlowQueryConfig.defaults());

        sink.accept(exec("select * from t1 where id = 1"));
        sink.accept(exec("select * from t2 where id = 1"));
        sink.accept(exec("select * from t3 where id = 1"));
        sink.accept(exec("select * from t1 where id = 2"));

        assertEquals(2, sink.stats().size(), "stats chặn ở maxFingerprints");
        assertEquals(1, sink.totalSkipQuery(), "1 fingerprint mới bị skip khi chạm trần");
        assertEquals(4, sink.totalQuery());
    }

    @Test
    void blank_fingerprint_is_skipped_without_npe() {
        InMemoryExecutionSink sink = new InMemoryExecutionSink(
                10, fingerprinter, 10, SlowQueryConfig.defaults());

        sink.accept(new QueryExecution("select 1", 1, Instant.now(), "ctx", null, true, List.of(), null));

        assertTrue(sink.stats().isEmpty());
        assertEquals(1, sink.totalSkipQuery());
        assertEquals(1, sink.recentExecutions().size());
    }
}
