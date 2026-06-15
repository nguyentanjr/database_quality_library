import org.example.collector.SchemaCollector;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import java.util.Map;
import java.util.TimeZone;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.*;

@Testcontainers
class SchemaCollectorTest {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }
    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
            .withInitScript("init.sql");   // đặt init.sql ở src/test/resources

    private DataSource ds() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(pg.getJdbcUrl());
        ds.setUser(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return ds;
    }

    @Test
    void reads_all_five_paths() throws Exception {

        SchemaSnapshot snap = new SchemaCollector().collect(ds());
        Map<String, Table> byName = snap.tables().stream()
                .collect(toMap(Table::name, t -> t));

        // 1. missing PK: transaction_log rỗng
        assertTrue(byName.get("transaction_log").primaryKey().isEmpty());

        // 2. suspicious type: amount = float8
        Table tx = byName.get("transactions");
       //ssertEquals("float8", colType(tx, "amount"));

        // 3. nullable: account_id nullable
      //assertTrue(colNullable(tx, "account_id"));

        // 4. FK hiện
        assertEquals("accounts", tx.foreignKeys().get(0).refTable());

        // 5. index bạn thêm hiện
        assertFalse(tx.indexes().isEmpty());
    }
}