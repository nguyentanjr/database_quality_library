package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.model.Column;
import org.example.model.Finding;
import org.example.model.Table;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;

import java.util.ArrayList;
import java.util.List;

public class R02_SuspiciousTypeRule implements Rule {

    public static final String ID = "R02_SuspiciousType";

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<Finding> findings = new ArrayList<>();
        for (Table table : context.snapshot().tables()) {
            for (Column column : table.columns()) {

                String name = column.name().toLowerCase();
                String type = column.type().toLowerCase();

                // Case 1: tiền mà dùng float
                if ((name.contains("amount")
                        || name.contains("price")
                        || name.contains("balance")
                        || name.contains("cost")
                        || name.contains("fee")
                        || name.contains("total"))
                        &&
                    (type.equals("float8")
                        || type.equals("float4")
                        || type.equals("double")
                        || type.equals("real"))
                ) {
                    findings.add(new Finding(
                            ID,
                            Severity.HIGH,
                            "Monetary column uses floating-point type",
                            "Use NUMERIC or DECIMAL for monetary values",
                            table.name(),
                            column.name()
                    ));
                }

                // Case 2: thời gian mà dùng varchar/text
                else if ((name.contains("_at")
                                || name.contains("_date")
                                || name.contains("_time")
                                || name.contains("created")
                                || name.contains("updated"))
                                &&
                                (type.equals("varchar")
                                        || type.equals("text")
                                        || type.equals("char")
                                        || type.equals("bpchar"))
                ) {
                    findings.add(new Finding(
                            ID,
                            Severity.MEDIUM,
                            "Temporal column uses string type",
                            "Use TIMESTAMP, DATE, or TIME instead",
                            table.name(),
                            column.name()
                    ));
                }
            }
        }
        return findings;
    }
}
