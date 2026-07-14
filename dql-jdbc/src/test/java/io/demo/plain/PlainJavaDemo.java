package io.demo.plain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.jdbc.Dql;
import org.example.jdbc.DqlRuntime;
import org.example.model.QualityReport;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;

public final class PlainJavaDemo {

    public static void main(String[] args) throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:dqldemo;DB_CLOSE_DELAY=-1");

        DemoWorkload.createFixtures(h2);

        // Toàn bộ tích hợp plain-Java nằm ở 2 dòng dưới đây.
        try (DqlRuntime dql = Dql.attach(h2)) {
            DataSource monitored = dql.dataSource();

            DemoWorkload.selectStar(monitored);              // R04
            DemoWorkload.nPlusOne(monitored, dql, 6);        // R09 fire
            DemoWorkload.repeatedSameParam(monitored, dql, 6); // R09 negative

            QualityReport report = dql.analyzeNow();

            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            System.out.println("=== QualityReport (plain Java, no Spring) ===");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            System.out.println("=== Collected queries (input transparency) ===");
            System.out.println(mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dql.collectedQueries()));
        }
    }
}
