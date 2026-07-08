package org.example.fingerprint;

import java.util.Locale;


public final class SqlVerbs {

    private static final String[] VERBS = {"select", "insert", "update", "delete"};

    private SqlVerbs() {
    }


    public static String mainVerb(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        String s = sql.strip().toLowerCase(Locale.ROOT);

        for (String verb : VERBS) {
            if (s.startsWith(verb)) {
                return verb;
            }
        }
        if (s.startsWith("with")) {
            return verbAfterCte(s);
        }
        return null;
    }

    public static boolean isSelect(String sql) {
        return "select".equals(mainVerb(sql));
    }

    private static String verbAfterCte(String s) {
        int depth = 0;
        boolean inString = false;

        for (int i = "with".length(); i < s.length(); i++) {
            char c = s.charAt(i);

            if (inString) {
                if (c == '\'') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '\'' -> inString = true;
                case '(' -> depth++;
                case ')' -> depth--;
                default -> {
                    if (depth == 0) {
                        String verb = verbAt(s, i);
                        if (verb != null) {
                            return verb;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String verbAt(String s, int i) {
        if (isWordChar(s.charAt(i)) && i > 0 && isWordChar(s.charAt(i - 1))) {
            return null; // đang ở giữa một identifier
        }
        for (String verb : VERBS) {
            if (s.startsWith(verb, i)) {
                int end = i + verb.length();
                if (end >= s.length() || !isWordChar(s.charAt(end))) {
                    return verb;
                }
            }
        }
        return null;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
