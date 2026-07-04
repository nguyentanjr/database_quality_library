import com.mysql.cj.jdbc.MysqlDataSource;
import org.example.collector.SchemaCollector;
import org.example.model.SchemaSnapshot;
import org.example.model.Table;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-M1: SchemaCollector.resolveScope hoạt động đúng trên MySQL (catalog-mode,
 * KHÔNG hardcode "public"), chỉ đọc bảng của database hiện tại — không lẫn bảng system.
 */
@Testcontainers
class SchemaCollectorMySqlTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("mysql-init.sql");

    private DataSource ds() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUser(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }

    @Test
    void reads_schema_on_mysql() throws Exception {
        SchemaSnapshot snap = new SchemaCollector().collect(ds());
        Map<String, Table> byName = snap.tables().stream()
                .collect(toMap(Table::name, t -> t));

        // 3 bảng fixture hiện diện
        assertTrue(byName.containsKey("accounts"));
        assertTrue(byName.containsKey("transactions"));
        assertTrue(byName.containsKey("transaction_log"));

        // KHÔNG lẫn bảng system MySQL (nếu scope sai = (null,null) thì mysql.user sẽ lọt vào)
        assertFalse(byName.containsKey("user"), "không được quét sang catalog 'mysql'");

        // PK đọc đúng
        assertEquals(List.of("id"), byName.get("accounts").primaryKey());
        assertTrue(byName.get("transaction_log").primaryKey().isEmpty());

        // FK + index đọc đúng
        Table tx = byName.get("transactions");
        assertEquals("account_id", tx.foreignKeys().get(0).column());
        assertEquals("accounts", tx.foreignKeys().get(0).refTable());
        assertFalse(tx.indexes().isEmpty());
    }
}
