package org.example.collector;

import org.example.model.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SchemaCollector {
    public SchemaSnapshot collect(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            MetadataScope scope = resolveScope(connection, metaData);
            List<String> tables = getTables(metaData, scope);
            List<Table> tableList = new ArrayList<>();
            for (String table : tables) {
                tableList.add(new Table(table,
                        getPrimaryKeys(metaData, scope, table),
                        getColumns(metaData, scope, table),
                        getForeignKeys(metaData, scope, table),
                        getIndexes(metaData, scope, table)));
            }
            return new SchemaSnapshot(tableList);
        }
    }

    private MetadataScope resolveScope(Connection connection, DatabaseMetaData metaData) throws SQLException {
        String product = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String catalog = safeCatalog(connection);
        String schema = safeSchema(connection);

        if (product.contains("mysql") || product.contains("mariadb")) {
            return new MetadataScope(catalog, null);
        }

        if (product.contains("postgresql")) {
            return new MetadataScope(catalog, schema != null ? schema : "public");
        }

        if (product.contains("h2") || product.contains("hsql")) {
            return new MetadataScope(catalog, schema != null ? schema : "PUBLIC");
        }

        if (product.contains("sql server")) {
            return new MetadataScope(catalog, schema != null ? schema : "dbo");
        }

        return new MetadataScope(catalog, schema);
    }

    private String safeCatalog(Connection connection) {
        try {
            return connection.getCatalog();
        } catch (SQLException e) {
            return null;
        }
    }

    private String safeSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError e) {
            return null;
        }
    }

    private List<String> getTables(DatabaseMetaData metaData, MetadataScope scope) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(scope.catalog(), scope.schemaPattern(), "%",new String[]{"TABLE"})) {
            while(rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private List<Column> getColumns(DatabaseMetaData metaData, MetadataScope scope, String tableName) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(scope.catalog(), scope.schemaPattern(), tableName, "%")) {
            while(rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                columns.add(new Column(name, type, nullable));
            }
        }
        return columns;
    }

    private List<Index> getIndexes(DatabaseMetaData metaData, MetadataScope scope, String tableName) throws SQLException {
        Map<String, IndexAccumulator> indexes = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(scope.catalog(), scope.schemaPattern(), tableName, false, false)) {
            while(rs.next()) {

                short type = rs.getShort("TYPE");
                if(type == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }

                String name = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                int ordinalPosition = rs.getShort("ORDINAL_POSITION");
                boolean unique = !rs.getBoolean("NON_UNIQUE");

                //sort theo danh sách key , thứ tự key trong index
                if(indexes.containsKey(name)) {
                    indexes.get(name).columns.put(ordinalPosition, columnName);
                }
                else {
                    IndexAccumulator acc = new IndexAccumulator(unique);
                    indexes.put(name, acc);
                    acc.columns.put(ordinalPosition, columnName);
                }
            }
        }

        List<Index> result = new ArrayList<>();
        for (Map.Entry<String, IndexAccumulator> entry : indexes.entrySet()) {

            List<String> columns = new ArrayList<>(entry.getValue().columns.values());
            boolean unique = entry.getValue().unique;

            result.add(new Index(
                    entry.getKey(),
                    columns,
                    unique
            ));
        }

        return result;
    }

    private static class IndexAccumulator {
        boolean unique;
        Map<Integer, String> columns = new TreeMap<>();

        IndexAccumulator(boolean unique) {
            this.unique = unique;
        }
    }

    private List<String> getPrimaryKeys(DatabaseMetaData metaData, MetadataScope scope, String tableName) throws SQLException {

        Map<Integer, String> primaryKeys = new TreeMap<>();

        try(ResultSet rs = metaData.getPrimaryKeys(scope.catalog(), scope.schemaPattern(), tableName)) {
            while(rs.next()) {
                String keyName = rs.getString("COLUMN_NAME");
                int keySeq = rs.getShort("KEY_SEQ");

                primaryKeys.put(keySeq, keyName);
            }
        }

        return new ArrayList<>(primaryKeys.values());
    }

    private List<ForeignKey> getForeignKeys(DatabaseMetaData metaData, MetadataScope scope, String tableName) throws SQLException {
        List<ForeignKey> fks = new ArrayList<>();
        Map<String, ForeignAccumulator> foreignKeys = new TreeMap<>();
        try (ResultSet rs = metaData.getImportedKeys(scope.catalog(), scope.schemaPattern(), tableName)) {
            while (rs.next()) {
                int keySeq = rs.getShort("KEY_SEQ");
                String fkName = rs.getString("FK_NAME");
                String fkcolumnName = rs.getString("FKCOLUMN_NAME");
                String refTable = rs.getString("PKTABLE_NAME");
                String refColumn = rs.getString("PKCOLUMN_NAME");
                fks.add(new ForeignKey(fkcolumnName, refTable, refColumn));
            }
            return fks;
        }

    }

    private record MetadataScope(String catalog, String schemaPattern) {
    }

    private static class ForeignAccumulator {
        List<String> columns = new ArrayList<>();
        List<String> refColumns = new ArrayList<>();
    }

}
