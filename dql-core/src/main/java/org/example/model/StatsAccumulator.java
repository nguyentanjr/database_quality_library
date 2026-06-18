package org.example.model;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public class StatsAccumulator {

    private final LongAdder count = new LongAdder();
    private final Set<String> tables;
    private final Set<String> columns;

    public StatsAccumulator(Set<String> tables, Set<String> columns) {
        this.tables = Set.copyOf(tables);
        this.columns = Set.copyOf(columns);
    }

    public LongAdder getCount() {
        return count;
    }

    public Set<String> getTables() {
        return tables;
    }

    public Set<String> getColumns() {
        return columns;
    }


    public static StatsAccumulator parseFrom(String fingerprint) {
        Set<String> tables = new HashSet<>();
        Set<String> columns = new HashSet<>();

        try {
            Statement statement = CCJSqlParserUtil.parse(fingerprint);

            TablesNamesFinder finder = new TablesNamesFinder();
            for (Object table : finder.getTables(statement)) {
                if (table instanceof String name) {
                    tables.add(normalize(name));
                }
            }

            if (statement instanceof Select select) {
                PlainSelect plainSelect = select.getPlainSelect();

                collectColumnsFromExpression(plainSelect.getWhere(), columns);

                if (plainSelect.getJoins() != null) {
                    for (Join join : plainSelect.getJoins()) {
                        if (join.getOnExpressions() != null) {
                            for (Expression onExpression : join.getOnExpressions()) {
                                collectColumnsFromExpression(onExpression, columns);
                            }
                        }
                    }
                }
            }

        } catch (JSQLParserException e) {
            System.out.println("LOG");
        }

        return new StatsAccumulator(tables, columns);
    }

    public void recordHit() {
        this.count.increment();
    }

    private static String normalize(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private static void collectColumnsFromExpression(Expression expression, Set<String> columns) {
        if (expression == null) {
            return;
        }

        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                columns.add(normalize(column.getColumnName()));
                return null;
            }
        }, null);
    }

}
