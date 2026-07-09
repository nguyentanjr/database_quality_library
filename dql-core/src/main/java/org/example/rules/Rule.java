package org.example.rules;

import org.example.model.Finding;

import java.util.List;

public interface Rule {
    List<Finding> analyze(AnalysisContext context);

    /**
     * ID để bật/tắt qua config (dql.rules.&lt;id&gt;.enabled). Mặc định suy từ class name
     * theo convention "&lt;ID&gt;Rule" (vd R08_UnusedIndexRule -> R08_UnusedIndex).
     * Rule không theo convention thì override.
     */
    default String id() {
        String simpleName = getClass().getSimpleName();
        return simpleName.endsWith("Rule")
                ? simpleName.substring(0, simpleName.length() - "Rule".length())
                : simpleName;
    }
}
