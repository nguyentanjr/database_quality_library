package org.example.context;

import org.example.model.QueryContext;

public class QueryContextHolder {

    private static final ThreadLocal<QueryContext> threadLocal = new ThreadLocal<>();

    public static void set(QueryContext context) {
        threadLocal.set(context);
    }

    public static QueryContext current() {
        return threadLocal.get();
    }

    public static void clear() {
        threadLocal.remove();;
    }

}
