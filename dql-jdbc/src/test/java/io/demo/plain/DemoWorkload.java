package io.demo.plain;

import org.example.jdbc.DqlRuntime;
import org.example.jdbc.UnitOfWork;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public final class DemoWorkload {

    private DemoWorkload() {
    }

    public static void createFixtures(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // R02: tiền để REAL (float); R03: FK nullable; R01: bảng không PK
            statement.execute("CREATE TABLE plain_payment (" +
                    "id INT PRIMARY KEY, amount REAL, note VARCHAR(50))");
            statement.execute("CREATE TABLE plain_no_pk (val VARCHAR(20))");
            statement.execute("CREATE TABLE plain_account (" +
                    "id INT PRIMARY KEY, owner_id INT, name VARCHAR(50)," +
                    "CONSTRAINT fk_owner FOREIGN KEY (owner_id) REFERENCES plain_payment(id))");
            for (int i = 1; i <= 10; i++) {
                statement.execute("INSERT INTO plain_payment VALUES (" + i + ", " + (i * 1.5) + ", 'n" + i + "')");
                statement.execute("INSERT INTO plain_account VALUES (" + i + ", " + i + ", 'acc" + i + "')");
            }
        }
    }
    public static int selectStar(DataSource dataSource) throws SQLException {
        int rows = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM plain_payment");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows++;
            }
        }
        return rows;
    }

    public static void nPlusOne(DataSource dataSource, DqlRuntime dql, int iterations) throws SQLException {
        try (UnitOfWork ignored = dql.beginUnitOfWork("demo-n-plus-one")) {
            for (int id = 1; id <= iterations; id++) {
                lookupAccount(dataSource, id);
            }
        }
    }

    public static void repeatedSameParam(DataSource dataSource, DqlRuntime dql, int iterations) throws SQLException {
        try (UnitOfWork ignored = dql.beginUnitOfWork("demo-dup-param")) {
            for (int i = 0; i < iterations; i++) {
                lookupAccount(dataSource, 1);
            }
        }
    }

    private static void lookupAccount(DataSource dataSource, int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT name FROM plain_account WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }
}
