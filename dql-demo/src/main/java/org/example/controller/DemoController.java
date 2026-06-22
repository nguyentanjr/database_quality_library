package org.example.controller;

import org.example.storage.ExecutionSink;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@RestController
public class DemoController {

    private final DataSource dataSource;
    private final ExecutionSink executionSink;

    public DemoController(
            DataSource dataSource,
            ExecutionSink executionSink
    ) {
        this.dataSource = dataSource;
        this.executionSink = executionSink;
    }

    @GetMapping("/demo-query")
    public String demoQuery() throws Exception {

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM accounts WHERE id = ?"
             )) {

            ps.setLong(1, 1);

            try (ResultSet rs = ps.executeQuery()) {
                String result = rs.next() ? "ok" : "empty";

                executionSink.stats().forEach(System.out::println);

                return result;
            }
        }
    }

    @GetMapping("/demo-n-plus-one")
    public String demoNPlusOne() throws Exception {

        try (Connection connection = dataSource.getConnection();
             PreparedStatement accountPs = connection.prepareStatement(
                     "SELECT id, email, balance FROM accounts"
             );
             ResultSet accountRs = accountPs.executeQuery()) {

            while (accountRs.next()) {
                long accountId = accountRs.getLong("id");

                try (PreparedStatement txPs = connection.prepareStatement(
                        "SELECT * FROM transactions WHERE account_id = ?"
                )) {
                    txPs.setLong(1, accountId);

                    try (ResultSet txRs = txPs.executeQuery()) {
                        while (txRs.next()) {
                            // consume transaction rows
                        }
                    }
                }
            }
        }

        return "n+1 demo done";
    }
}