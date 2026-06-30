package org.example.schema;

import org.example.collector.SchemaCollector;
import org.example.model.SchemaSnapshot;

import javax.sql.DataSource;
import java.sql.SQLException;

public class SchemaSnapshotProvider {

    private final DataSource dataSource;
    private volatile SchemaSnapshot cached;

    public SchemaSnapshotProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SchemaSnapshot get() throws SQLException {
        if (cached == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = new SchemaCollector().collect(dataSource);
                }
            }
        }
        return cached;
    }
}