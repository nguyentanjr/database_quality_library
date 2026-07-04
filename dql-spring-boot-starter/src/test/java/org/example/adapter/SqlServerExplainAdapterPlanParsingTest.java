package org.example.adapter;

import org.example.model.ExplainResult;
import org.example.model.QueryExecution;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerExplainAdapterPlanParsingTest {

    @Test
    void parses_largest_full_scan_from_showplan_xml() throws Exception {
        SqlServerExplainAdapter adapter = new SqlServerExplainAdapter(new ShowplanDataSource("""
                <ShowPlanXML xmlns="http://schemas.microsoft.com/sqlserver/2004/07/showplan">
                  <BatchSequence>
                    <Batch>
                      <Statements>
                        <StmtSimple StatementText="select * from owners" StatementSubTreeCost="88.5">
                          <QueryPlan>
                            <RelOp PhysicalOp="Index Seek" EstimateRows="1" EstimatedTotalSubtreeCost="0.01">
                              <IndexScan>
                                <Object Database="[petclinic]" Schema="[dbo]" Table="[owners]" Index="[ix_owner_id]" />
                              </IndexScan>
                            </RelOp>
                            <RelOp PhysicalOp="Table Scan" EstimateRows="12001" EstimatedTotalSubtreeCost="88.5">
                              <TableScan>
                                <Object Database="[petclinic]" Schema="[dbo]" Table="[owners]" />
                              </TableScan>
                            </RelOp>
                            <RelOp PhysicalOp="Clustered Index Scan" EstimateRows="100" EstimatedTotalSubtreeCost="1.5">
                              <IndexScan>
                                <Object Database="[petclinic]" Schema="[dbo]" Table="[pets]" Index="[pk_pets]" />
                              </IndexScan>
                            </RelOp>
                          </QueryPlan>
                        </StmtSimple>
                      </Statements>
                    </Batch>
                  </BatchSequence>
                </ShowPlanXML>
                """));

        Optional<ExplainResult> result = adapter.explain(exec("select * from owners"));

        assertTrue(result.isPresent());
        assertEquals("owners", result.get().tableName());
        assertEquals(12001, result.get().estimatedRows());
        assertEquals(88.5, result.get().estimatedCost());
        assertEquals("Table Scan", result.get().reason());
    }

    private QueryExecution exec(String sqlRaw) {
        return new QueryExecution(sqlRaw, 1, Instant.now(), "ctx", "fp", true, List.of(), null);
    }

    private static final class ShowplanDataSource implements DataSource {
        private final String xml;

        private ShowplanDataSource(String xml) {
            this.xml = xml;
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createStatement" -> statement();
                        case "prepareStatement" -> preparedStatement(xml);
                        case "setReadOnly", "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        private Statement statement() {
            return (Statement) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "execute" -> true;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private PreparedStatement preparedStatement(String xml) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "executeQuery" -> resultSet(xml);
                        case "setQueryTimeout", "setObject", "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet resultSet(String xml) {
            final int[] index = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "next" -> ++index[0] == 0;
                        case "getString" -> xml;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
