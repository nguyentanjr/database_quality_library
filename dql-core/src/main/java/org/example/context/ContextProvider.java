package org.example.context;

import org.example.model.QueryContext;

public interface ContextProvider {

    QueryContext currentContext();

    void clearContext();
}
