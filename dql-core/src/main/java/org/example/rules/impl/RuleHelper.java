package org.example.rules.impl;

import org.example.model.Index;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuleHelper {

    public static Set<String> leadingIndices(List<Index> indices) {
        Set<String> leadingIndexedColumns = new HashSet<>();
        for(Index index : indices) {
            List<String> cols = index.indexColumns();
            if (cols == null || cols.isEmpty()) continue;
            String firstPosition = index.indexColumns().getFirst();
            leadingIndexedColumns.add(firstPosition);
        }
        return leadingIndexedColumns;
    }
}
