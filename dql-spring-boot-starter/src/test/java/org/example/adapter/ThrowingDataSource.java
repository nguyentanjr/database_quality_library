package org.example.adapter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * DataSource "mìn": {@link #getConnection()} luôn ném để chứng minh rằng với SQL
 * KHÔNG an toàn, adapter phải trả {@code Optional.empty()} mà TUYỆT ĐỐI không chạm DB.
 * Với SELECT an toàn thì adapter mới đi tiếp và ném marker này.
 */
final class ThrowingDataSource implements DataSource {

    static final String MARKER = "DataSource was touched";

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException(MARKER);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException(MARKER);
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
}
