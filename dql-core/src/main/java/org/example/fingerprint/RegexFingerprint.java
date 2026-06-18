package org.example.fingerprint;

import java.util.regex.Pattern;

public class RegexFingerprint implements Fingerprinter{

    private static final Pattern QUOTED_STRING =
            Pattern.compile("'[^']*'");

    private static final Pattern NUMBER =
            Pattern.compile("\\b\\d+\\b");

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+");

    private static final Pattern IN_LIST =
            Pattern.compile("\\bin\\s*\\((\\s*\\?\\s*,)+\\s*\\?\\s*\\)");


    @Override
    public String fingerprint(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            return "";
        }

        String result = rawSql.toLowerCase();

        result = QUOTED_STRING.matcher(result).replaceAll("?");
        result = NUMBER.matcher(result).replaceAll("?");
        result = WHITESPACE.matcher(result).replaceAll(" ").trim();
        result = IN_LIST.matcher(result).replaceAll("in (?)");

        return result;
    }
}
