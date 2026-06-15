package org.example;


import org.example.collector.SchemaCollector;
import org.example.model.SchemaSnapshot;
import org.postgresql.ds.PGSimpleDataSource;

import java.util.TimeZone;

public class Main {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl("jdbc:postgresql://localhost:5432/dqldemo");
        ds.setUser("dql");
        ds.setPassword("dql");

        SchemaSnapshot snap = new SchemaCollector().collect(ds);
        snap.tables().forEach(System.out::println);  // record toString in đủ field
    }
}