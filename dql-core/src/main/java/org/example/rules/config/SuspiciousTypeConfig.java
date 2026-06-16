package org.example.rules.config;

import org.example.enums.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record SuspiciousTypeConfig(List<TypeRule> rules) {



    private record TypeRule(List<String> columns,
                                      Set<String> badTypes,
                                      Severity severity,
                                      String message,
                                      String suggestion) {};
}

