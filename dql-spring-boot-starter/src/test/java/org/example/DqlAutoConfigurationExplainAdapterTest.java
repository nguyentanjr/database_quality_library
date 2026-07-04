package org.example;

import org.example.adapter.MariaDbExplainAdapter;
import org.example.adapter.MySqlExplainAdapter;
import org.example.adapter.PostgresExplainAdapter;
import org.example.adapter.SqlServerExplainAdapter;
import org.example.explain.ExplainAdapter;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DqlAutoConfigurationExplainAdapterTest {

    private final DqlAutoConfiguration configuration = new DqlAutoConfiguration();

    @Test
    void selects_mariadb_adapter() {
        ExplainAdapter adapter = configuration.explainAdapter(dataSource("MariaDB"));

        assertInstanceOf(MariaDbExplainAdapter.class, adapter);
    }

    @Test
    void selects_mysql_adapter() {
        ExplainAdapter adapter = configuration.explainAdapter(dataSource("MySQL"));

        assertInstanceOf(MySqlExplainAdapter.class, adapter);
    }

    @Test
    void selects_sql_server_adapter() {
        ExplainAdapter adapter = configuration.explainAdapter(dataSource("Microsoft SQL Server"));

        assertInstanceOf(SqlServerExplainAdapter.class, adapter);
    }

    @Test
    void falls_back_to_postgres_adapter_when_product_detection_fails() {
        ExplainAdapter adapter = configuration.explainAdapter(new ThrowingProductDataSource());

        assertInstanceOf(PostgresExplainAdapter.class, adapter);
    }

    private DataSource dataSource(String productName) {
        return new ProductNameDataSource(productName);
    }

    private static class ProductNameDataSource implements DataSource {
        private final String productName;

        protected ProductNameDataSource(String productName) {
            this.productName = productName;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMetaData" -> databaseMetaData(productName);
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        private static DatabaseMetaData databaseMetaData(String productName) {
            return (DatabaseMetaData) Proxy.newProxyInstance(
                    ProductNameDataSource.class.getClassLoader(),
                    new Class<?>[]{DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if ("getDatabaseProductName".equals(method.getName())) {
                            return productName;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static final class ThrowingProductDataSource extends ProductNameDataSource {
        private ThrowingProductDataSource() {
            super("unused");
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("product unavailable");
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
