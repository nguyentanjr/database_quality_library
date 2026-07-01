import org.example.fingerprint.Fingerprinter;
import org.example.fingerprint.RegexFingerprint;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.rules.config.SlowQueryConfig;
import org.example.storage.InMemoryExecutionSink;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QueryStatsIT {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
            .withInitScript("init.sql");

    private DataSource ds() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(pg.getJdbcUrl());
        ds.setUser(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return ds;
    }

    @Test
    void collects_fingerprint_count_tables_and_columns() throws Exception {
        Fingerprinter fingerprinter = new RegexFingerprint();

        InMemoryExecutionSink sink = new InMemoryExecutionSink(
                100,
                fingerprinter,
                100, SlowQueryConfig.defaults()
        );

        executeAndAccept(sink, "SELECT * FROM accounts WHERE id = 1");
        executeAndAccept(sink, "SELECT * FROM accounts WHERE id = 2");
        executeAndAccept(sink, "SELECT * FROM accounts WHERE id = 999");

        executeAndAccept(sink, """
                SELECT * FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE t.amount > 5
                """);

        List<QueryStats> stats = sink.stats();

        QueryStats accounts = findByFingerprint(
                stats,
                "select * from accounts where id = ?"
        );

        assertEquals(3, accounts.count());
        assertEquals(Set.of("accounts"), accounts.tables());
        assertEquals(Set.of("id"), accounts.columns());

        QueryStats join = findContaining(stats, "join accounts");

        assertEquals(1, join.count());
        assertTrue(join.tables().contains("transactions"));
        assertTrue(join.tables().contains("accounts"));

        assertTrue(join.columns().contains("account_id"));
        assertTrue(join.columns().contains("amount"));

        // a.id trong JOIN ON cũng có thể được visitor thu vào, nên không assert bằng Set.of(...)
        assertTrue(join.columns().contains("id"));
    }

    private void executeAndAccept(InMemoryExecutionSink sink, String sql) throws Exception {
        long start = System.nanoTime();

        try (Connection connection = ds().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }

        long duration = (System.nanoTime() - start) / 1_000_000;

        QueryExecution queryExecution = new QueryExecution(
                sql,
                duration,
                Instant.now(),
                null,
                null,
                List.of(),
                null
        );

        sink.accept(queryExecution);
    }

    private QueryStats findByFingerprint(List<QueryStats> stats, String fingerprint) {
        return stats.stream()
                .filter(s -> fingerprint.equals(s.fingerprint()))
                .findFirst()
                .orElseThrow();
    }

    private QueryStats findContaining(List<QueryStats> stats, String text) {
        return stats.stream()
                .filter(s -> s.fingerprint().contains(text))
                .findFirst()
                .orElseThrow();
    }
}