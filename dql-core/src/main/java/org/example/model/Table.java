package org.example.model;

import java.util.List;

public record Table(String name,
                    List<String> primaryKey,
                    List<Column> columns,
                    List<ForeignKey> foreignKeys,
                    List<Index> indexes) {}
