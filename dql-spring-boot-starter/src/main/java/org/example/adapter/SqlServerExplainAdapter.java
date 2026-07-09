package org.example.adapter;

import org.example.explain.ExplainAdapter;
import org.example.fingerprint.SqlVerbs;
import org.example.model.ExplainResult;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class SqlServerExplainAdapter implements ExplainAdapter {

    private static final Pattern FOR_UPDATE_PATTERN =
            Pattern.compile("\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;

    public SqlServerExplainAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ExplainResult> explain(QueryExecution execution) throws SQLException {
        if (execution == null || execution.sqlRaw() == null) {
            return Optional.empty();
        }

        String rawSql = execution.sqlRaw().strip();

        if (!isSafeSelect(rawSql)) {
            return Optional.empty();
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            enableShowplan(conn);

            try (PreparedStatement ps = conn.prepareStatement(rawSql)) {
                ps.setQueryTimeout(3);
                bindParams(ps, execution);

                try (ResultSet rs = ps.executeQuery()) {
                    List<ExplainResult> fullScans = new ArrayList<>();
                    while (rs.next()) {
                        collectFullScans(rs.getString(1), fullScans);
                    }
                    return fullScans.stream()
                            .max(Comparator.comparingLong(ExplainResult::estimatedRows));
                } catch (Exception e) {
                    return Optional.empty();
                }
            } finally {
                disableShowplan(conn);
            }
        }
    }

    private boolean isSafeSelect(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        boolean plainSelect = lower.length() > "select".length()
                && lower.startsWith("select")
                && Character.isWhitespace(lower.charAt("select".length()));
        boolean cteSelect = lower.startsWith("with") && SqlVerbs.isSelect(sql);
        return (plainSelect || cteSelect)
                && !lower.contains(";")
                && !FOR_UPDATE_PATTERN.matcher(lower).find();
    }

    private void enableShowplan(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("SET SHOWPLAN_XML ON");
        }
    }

    private void disableShowplan(Connection conn) {
        try (Statement statement = conn.createStatement()) {
            statement.execute("SET SHOWPLAN_XML OFF");
        } catch (SQLException ignored) {
            // Best-effort cleanup. The connection is closed by the caller immediately after this.
        }
    }

    private void collectFullScans(String xml, List<ExplainResult> fullScans) {
        if (xml == null || xml.isBlank()) {
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            NodeList relOps = document.getElementsByTagNameNS("*", "RelOp");

            for (int i = 0; i < relOps.getLength(); i++) {
                Element relOp = (Element) relOps.item(i);
                String physicalOp = relOp.getAttribute("PhysicalOp");

                if (!isFullScan(physicalOp)) {
                    continue;
                }

                fullScans.add(new ExplainResult(
                        true,
                        tableName(relOp),
                        Math.round(parseDouble(firstNonBlank(
                                relOp.getAttribute("EstimateRows"),
                                relOp.getAttribute("EstimatedRows")
                        ))),
                        parseDouble(relOp.getAttribute("EstimatedTotalSubtreeCost")),
                        physicalOp
                ));
            }
        } catch (Exception ignored) {
            // Malformed or unsupported plan XML: adapter simply returns no R10 evidence.
        }
    }

    private boolean isFullScan(String physicalOp) {
        if (physicalOp == null) {
            return false;
        }
        String op = physicalOp.toLowerCase(Locale.ROOT);
        return "table scan".equals(op)
                || "clustered index scan".equals(op)
                || "columnstore index scan".equals(op);
    }

    private String tableName(Element relOp) {
        NodeList objects = relOp.getElementsByTagNameNS("*", "Object");
        if (objects.getLength() == 0) {
            return null;
        }

        Element object = (Element) objects.item(0);
        return cleanIdentifier(firstNonBlank(
                object.getAttribute("Table"),
                object.getAttribute("Index"),
                object.getAttribute("Schema")
        ));
    }

    private String cleanIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void bindParams(PreparedStatement ps, QueryExecution execution) throws SQLException {
        List<List<QueryParam>> paramSets = execution.paramSets();

        if (paramSets == null || paramSets.isEmpty()) {
            return;
        }

        List<QueryParam> params = paramSets.get(0);

        for (QueryParam param : params) {
            ps.setObject(param.index(), param.value());
        }
    }
}
