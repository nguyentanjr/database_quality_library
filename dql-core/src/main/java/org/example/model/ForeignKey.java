package org.example.model;

public record ForeignKey(String column, String refTable, String refColumn) {
}
