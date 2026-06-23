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
    @GetMapping("/demo-repeat-same-param")
    public String demoRepeatSameParam() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (int i = 0; i < 6; i++) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM transactions WHERE account_id = ?"
                )) {
                    ps.setLong(1, 1);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            // consume rows
                        }
                    }
                }
            }
        }

        return "repeat same param demo done";
    }
    @GetMapping("/demo-n-plus-one-5")
    public String demoNPlusOne5() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (long accountId = 1; accountId <= 5; accountId++) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM transactions WHERE account_id = ?"
                )) {
                    ps.setLong(1, accountId);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            // consume rows
                        }
                    }
                }
            }
        }

        return "n+1 demo 5 done";
    }
}