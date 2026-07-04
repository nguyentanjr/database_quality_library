package org.example.adapter;

import org.example.model.QueryExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerExplainAdapterSafetyTest {

    private final SqlServerExplainAdapter adapter = new SqlServerExplainAdapter(new ThrowingDataSource());

    private QueryExecution exec(String sqlRaw) {
        return new QueryExecution(sqlRaw, 1, Instant.now(), "ctx", "fp", true, List.of(), null);
    }

    @Test
    void blocks_statement_with_semicolon() throws Exception {
        assertTrue(adapter.explain(exec("select * from orders where id = 1;")).isEmpty());
    }

    @Test
    void blocks_for_update() throws Exception {
        assertTrue(adapter.explain(exec("select * from orders where id = 1 for update")).isEmpty());
    }

    @Test
    void blocks_non_select() throws Exception {
        assertTrue(adapter.explain(exec("update orders set status = 'X' where id = 1")).isEmpty());
        assertTrue(adapter.explain(exec("delete from orders where id = 1")).isEmpty());
    }

    @Test
    void blocks_word_starting_with_select_but_not_a_select() throws Exception {
        assertTrue(adapter.explain(exec("selection from x")).isEmpty());
    }

    @Test
    void allows_cte_select() {
        Exception e = assertThrows(Exception.class,
                () -> adapter.explain(exec("with c as (select 1 as n) select n from c")));
        assertEquals(ThrowingDataSource.MARKER, e.getMessage());
    }

    @Test
    void blocks_data_modifying_cte() throws Exception {
        assertTrue(adapter.explain(exec("with c as (select 1) insert into t select * from c")).isEmpty());
    }

    @Test
    void safe_select_proceeds_to_touch_datasource() {
        Exception e = assertThrows(Exception.class,
                () -> adapter.explain(exec("select * from orders where id = 1")));
        assertEquals(ThrowingDataSource.MARKER, e.getMessage());
    }
}
