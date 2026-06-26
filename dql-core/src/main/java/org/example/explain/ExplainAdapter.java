package org.example.explain;

import org.example.model.ExplainResult;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ExplainAdapter {

    Optional<ExplainResult> explain(QueryExecution execution) throws SQLException;
}
