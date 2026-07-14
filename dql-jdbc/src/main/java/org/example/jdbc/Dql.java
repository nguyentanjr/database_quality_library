package org.example.jdbc;

import javax.sql.DataSource;

public final class Dql {

    private Dql() {
    }

    public static DqlRuntime attach(DataSource dataSource) {
        return attach(dataSource, DqlJdbcConfig.defaults());
    }

    public static DqlRuntime attach(DataSource dataSource, DqlJdbcConfig config) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        return new DqlRuntime(dataSource, config);
    }
}
