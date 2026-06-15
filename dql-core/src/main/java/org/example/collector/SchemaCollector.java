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
            List<String> tables = getTables(metaData);
            List<Table> tableList = new ArrayList<>();
            for (String table : tables) {
                tableList.add(new Table(table,
                        getPrimaryKeys(metaData, table),
                        getColumns(metaData, table),
                        getForeignKeys(metaData, table),
                        getIndexes(metaData, table)));
            }
            return new SchemaSnapshot(tableList);
        }
    }

    private List<String> getTables(DatabaseMetaData metaData) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(null, "public", "%",new String[]{"TABLE"})) {
            while(rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private List<Column> getColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(null, "public", tableName, "%")) {
            while(rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                columns.add(new Column(name, type, nullable));
            }
        }
        return columns;
    }

    private List<Index> getIndexes(DatabaseMetaData metaData, String tableName) throws SQLException {
        Map<String, IndexAccumulator> indexes = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(null, "public", tableName, false, false)) {
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

    private List<String> getPrimaryKeys(DatabaseMetaData metaData, String tableName) throws SQLException {

        Map<Integer, String> primaryKeys = new TreeMap<>();

        try(ResultSet rs = metaData.getPrimaryKeys(null, "public", tableName)) {
            while(rs.next()) {
                String keyName = rs.getString("COLUMN_NAME");
                int keySeq = rs.getShort("KEY_SEQ");

                primaryKeys.put(keySeq, keyName);
            }
        }

        return new ArrayList<>(primaryKeys.values());
    }

    private List<ForeignKey> getForeignKeys(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ForeignKey> fks = new ArrayList<>();
        Map<String, ForeignAccumulator> foreignKeys = new TreeMap<>();
        try (ResultSet rs = metaData.getImportedKeys(null, "public", tableName)) {
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

    private static class ForeignAccumulator {
        List<String> columns = new ArrayList<>();
        List<String> refColumns = new ArrayList<>();
    }

}
