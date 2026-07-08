package org.example.callsite;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class SourceLocationResolver {

    private static final StackWalker WALKER = StackWalker.getInstance();

    private static final List<String> INTERNAL_PREFIXES = List.of(
            "java.",
            "javax.",
            "jakarta.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.springframework.",
            "org.hibernate.",
            "org.postgresql.",
            "com.mysql.",
            "org.mariadb.",
            "com.microsoft.sqlserver.",
            "org.h2.",
            "org.hsqldb.",
            "com.zaxxer.",
            "net.ttddyy.",
            "com.fasterxml.jackson.",
            "org.apache.catalina.",
            "org.apache.tomcat.",
            "org.example."
    );

    private static final List<String> PROXY_MARKERS = List.of(
            "$HibernateProxy$",
            "$$EnhancerBy",
            "$$SpringCGLIB$$",
            "$Proxy",
            "$$Lambda"
    );

    private SourceLocationResolver() {
    }

    public static String resolve(SourceLocationConfig config) {
        SourceLocationConfig effective = config == null ? SourceLocationConfig.defaults() : config;
        if (!effective.enabled()) {
            return null;
        }
        return WALKER.walk(frames -> resolve(frames, effective).orElse(null));
    }

    public static Optional<String> resolve(Stream<StackFrame> frames, SourceLocationConfig config) {
        SourceLocationConfig effective = config == null ? SourceLocationConfig.defaults() : config;
        if (!effective.enabled()) {
            return Optional.empty();
        }
        return frames
                .limit(effective.maxDepth())
                .filter(frame -> !isInternal(
                        frame.getClassName(),
                        effective.applicationPrefixes(),
                        effective.extraInternalPrefixes()
                ))
                .findFirst()
                .map(SourceLocationResolver::format);
    }

    private static boolean isInternal(
            String className,
            List<String> applicationPrefixes,
            List<String> extraInternalPrefixes
    ) {
        if (className == null || className.isBlank()) {
            return true;
        }
        for (String marker : PROXY_MARKERS) {
            if (className.contains(marker)) {
                return true;
            }
        }
        if (startsWithAny(className, applicationPrefixes)) {
            return false;
        }
        for (String prefix : INTERNAL_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : extraInternalPrefixes) {
            if (prefix != null && !prefix.isBlank() && className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String className, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String format(StackFrame frame) {
        String fileName = frame.getFileName();
        int lineNumber = frame.getLineNumber();
        String source = fileName == null || fileName.isBlank()
                ? "Unknown Source"
                : lineNumber >= 0 ? fileName + ":" + lineNumber : fileName;
        return frame.getClassName() + "." + frame.getMethodName() + "(" + source + ")";
    }
}
