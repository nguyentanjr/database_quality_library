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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class PostgresExplainAdapter implements ExplainAdapter {

    private static final Pattern FOR_UPDATE_PATTERN =
            Pattern.compile("\\bfor\\s+update\\b", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public PostgresExplainAdapter(DataSource dataSource) {
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

        String explainSql = "EXPLAIN (FORMAT JSON) " + rawSql;

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

                JsonNode plan = root.path(0).path("Plan");
                if (plan.isMissingNode()) {
                    return Optional.empty();
                }

                return findLargestFullScan(plan);
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

    private Optional<ExplainResult> findLargestFullScan(JsonNode plan) {
        List<ExplainResult> fullScans = new ArrayList<>();
        collectFullScans(plan, fullScans);

        return fullScans.stream()
                .max(Comparator.comparingLong(ExplainResult::estimatedRows));
    }

    private void collectFullScans(JsonNode plan, List<ExplainResult> fullScans) {
        if (plan == null || plan.isMissingNode()) {
            return;
        }

        String nodeType = plan.path("Node Type").asText(null);

        if ("Seq Scan".equals(nodeType)) {
            String relationName = plan.path("Relation Name").asText(null);
            long estimatedRows = plan.path("Plan Rows").asLong(0);
            double totalCost = plan.path("Total Cost").asDouble(0.0);

            fullScans.add(new ExplainResult(
                    true,
                    relationName,
                    estimatedRows,
                    totalCost,
                    nodeType
            ));
        }

        JsonNode children = plan.path("Plans");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectFullScans(child, fullScans);
            }
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
