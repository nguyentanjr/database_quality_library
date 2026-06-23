package org.example.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.explain.ExplainAdapter;
import org.example.model.ExplainResult;
import org.example.model.QueryExecution;
import org.example.model.QueryParam;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PostgresExplainAdapter implements ExplainAdapter {

    private final DataSource dataSource;

    public PostgresExplainAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ExplainResult explain(QueryExecution execution) throws SQLException {


        String explainSql = "EXPLAIN (FORMAT JSON) " + execution.sqlRaw();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(explainSql)) {

            bindParams(ps, execution);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString(1);
                    root = mapper.readTree(json);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            JsonNode plan = root.get(0).get("Plan");

            String nodeType = plan.get("Node Type").asText();
            String relationName = plan.get("Relation Name").asText();
            String indexName = plan.get("Index Name").asText();

            if ("Seq Scan".equals(nodeType)) {
                // full table scan
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