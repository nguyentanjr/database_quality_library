package org.example.adapter;

import javax.sql.DataSource;

public class MariaDbExplainAdapter extends MySqlExplainAdapter {

    public MariaDbExplainAdapter(DataSource dataSource) {
        super(dataSource);
    }
}
