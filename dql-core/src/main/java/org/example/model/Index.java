package org.example.model;

import java.util.List;

public record Index(String indexName, List<String> indexColumns, boolean unique) {
}
