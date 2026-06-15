package org.example.model;

import java.util.List;

public record SchemaSnapshot(List<Table> tables) {}