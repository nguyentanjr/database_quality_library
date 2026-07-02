package org.example.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.explain.ExplainAdapter;
import org.example.model.ExplainResult;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class MySqlExplainAdapter implements ExplainAdapter {

    private static final Pattern FOR_UPDATE_PATTERN =
            Pattern.compile("\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public MySqlExplainAdapter(DataSource dataSource) {
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

        String explainSql = "EXPLAIN FORMAT=JSON " + rawSql;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(explainSql)) {

            conn.setReadOnly(true);
            ps.setQueryTimeout(3);

            bindParams(ps, execution);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                String json = rs.getString(1);
                JsonNode root = mapper.readTree(json);

                return findLargestFullScan(root);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    private boolean isSafeSelect(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.length() > "select".length()
                && lower.startsWith("select")
                && Character.isWhitespace(lower.charAt("select".length()))
                && !lower.contains(";")
                && !FOR_UPDATE_PATTERN.matcher(lower).find();
    }

    private Optional<ExplainResult> findLargestFullScan(JsonNode root) {
        List<ExplainResult> fullScans = new ArrayList<>();
        collectFullScans(root, fullScans);

        return fullScans.stream()
                .max(Comparator.comparingLong(ExplainResult::estimatedRows));
    }

    private void collectFullScans(JsonNode node, List<ExplainResult> fullScans) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        if (node.isObject()) {
            String accessType = node.path("access_type").asText(null);
            if ("ALL".equalsIgnoreCase(accessType)) {
                String tableName = node.path("table_name").asText(null);
                long estimatedRows = node.has("rows_examined_per_scan")
                        ? node.path("rows_examined_per_scan").asLong(0)
                        : node.path("rows_produced_per_join").asLong(0);
                double readCost = parseCost(node.path("cost_info").path("read_cost").asText(null));

                fullScans.add(new ExplainResult(
                        true,
                        tableName,
                        estimatedRows,
                        readCost,
                        "access_type=ALL"
                ));
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                collectFullScans(fields.next().getValue(), fullScans);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectFullScans(child, fullScans);
            }
        }
    }

    private double parseCost(String cost) {
        if (cost == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(cost);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void bindParams(PreparedStatement ps, QueryExecution execution)
            throws SQLException {

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
