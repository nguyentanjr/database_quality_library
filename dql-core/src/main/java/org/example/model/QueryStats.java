package org.example.model;

import java.util.Set;

public record QueryStats(String fingerprint, long count, Set<String> tables, Set<String> columns) {
}
