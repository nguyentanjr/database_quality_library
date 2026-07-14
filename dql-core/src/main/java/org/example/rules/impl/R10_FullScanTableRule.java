package org.example.rules.impl;

import org.example.enums.Severity;
import org.example.explain.ExplainAdapter;
import org.example.model.ExplainResult;
import org.example.model.Finding;
import org.example.model.QueryExecution;
import org.example.model.QueryStats;
import org.example.rules.AnalysisContext;
import org.example.rules.Rule;
import org.example.rules.config.FullScanConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class R10_FullScanTableRule implements Rule {
    public static final String ID = "R10_FullScanTable";

    public static final Severity SEVERITY = Severity.HIGH;

    private static final Logger LOGGER = Logger.getLogger(R10_FullScanTableRule.class.getName());

    private final ExplainAdapter explainAdapter;
    private final FullScanConfig config;

    // Cache verdict full-scan theo fingerprint (EXPLAIN 1 lần rồi nhớ):
    //   Optional.of(result) = full-scan đã xác nhận  -> emit
    //   Optional.empty()    = đã EXPLAIN, sạch/dưới ngưỡng -> skip, khỏi EXPLAIN lại
    //   absent              = chưa biết -> EXPLAIN nếu còn ngân sách + có param mẫu
    // Chạy 1 thread (scheduler) nên HashMap đủ. Sống cả đời process: schema tĩnh
    // (SchemaSnapshotProvider lazy-once) -> verdict tĩnh, không cần invalidate.
    // GIẢ ĐỊNH: thêm index lúc runtime cần restart để cache cập nhật.
    private final Map<String, Optional<ExplainResult>> verdictCache = new HashMap<>();

    public R10_FullScanTableRule(
            ExplainAdapter explainAdapter,
            FullScanConfig config
    ) {
        this.explainAdapter = Objects.requireNonNull(explainAdapter, "explainAdapter");
        this.config = Objects.requireNonNullElse(config, FullScanConfig.defaults());
    }

    @Override
    public List<Finding> analyze(AnalysisContext context) {
        List<QueryStats> queryStats = context.queryStats() == null
                ? List.of()
                : context.queryStats();
        Map<String, QueryExecution> sampleByFingerprint = buildSamples(context);

        // Ứng viên = SELECT có WHERE/JOIN. Sort count desc chỉ để ưu tiên EXPLAIN
        // fingerprint hay dùng trước — KHÔNG cắt tập (limit chỉ chặn EXPLAIN mới bên dưới).
        List<QueryStats> eligible = queryStats.stream()
                .filter(Objects::nonNull)
                .filter(QueryStats::select)
                .filter(stats -> stats.columns() != null && !stats.columns().isEmpty())
                .sorted(Comparator.comparingLong(QueryStats::count).reversed())
                .toList();

        List<Finding> findings = new ArrayList<>();
        int explainBudget = config.maxExplainCandidates();

        for (QueryStats stats : eligible) {
            String fingerprint = stats.fingerprint();

            // 1) Đã biết verdict -> emit từ cache, KHÔNG EXPLAIN, KHÔNG tính budget.
            //    Đây là điểm diệt bug "fire rồi biến mất": full-scan còn trong stats
            //    vẫn emit dù ring ghi đè param, dù rớt khỏi ngân sách EXPLAIN.
            if (verdictCache.containsKey(fingerprint)) {
                verdictCache.get(fingerprint)
                        .ifPresent(explain -> findings.add(toFinding(fingerprint, explain)));
                continue;
            }

            // 2) Chưa biết -> cần EXPLAIN, nhưng giới hạn số EXPLAIN MỚI mỗi chu kỳ.
            //    Hết ngân sách thì để UNKNOWN, chu kỳ sau thử tiếp (hoãn, không mất).
            if (explainBudget <= 0) {
                continue;
            }

            QueryExecution sample = sampleByFingerprint.get(fingerprint);
            if (sample == null) {
                // Chưa mượn được param -> KHÔNG cache (đây là "chưa biết", không phải "sạch").
                continue;
            }

            explainBudget--; // commit 1 lần EXPLAIN (thành hay bại đều tính vào ngân sách)
            try {
                Optional<ExplainResult> result = explainAdapter.explain(sample);
                if (result.isEmpty()) {
                    continue; // adapter không plan được -> để UNKNOWN, thử lại chu kỳ sau
                }

                ExplainResult explain = result.get();
                if (explain.fullScan() && explain.estimatedRows() >= config.minEstimatedRows()) {
                    verdictCache.put(fingerprint, Optional.of(explain));
                    findings.add(toFinding(fingerprint, explain));
                } else {
                    verdictCache.put(fingerprint, Optional.empty());
                }
            } catch (Exception e) {
                // Lỗi EXPLAIN -> KHÔNG cache, thử lại chu kỳ sau.
                LOGGER.fine(() -> "Skip R10 full scan explain failure for fingerprint: " + fingerprint);
            }
        }

        return findings;
    }

    private Map<String, QueryExecution> buildSamples(AnalysisContext context) {
        List<QueryExecution> recentExecutions = context.recentExecutions() == null
                ? List.of()
                : context.recentExecutions();

        Map<String, QueryExecution> sampleByFingerprint = new HashMap<>();
        for (QueryExecution execution : recentExecutions) {
            if (execution == null || execution.fingerprint() == null) {
                continue;
            }
            sampleByFingerprint.putIfAbsent(execution.fingerprint(), execution);
        }
        return sampleByFingerprint;
    }

    private Finding toFinding(String fingerprint, ExplainResult explain) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("estimatedRows", explain.estimatedRows());
        details.put("estimatedCost", explain.estimatedCost());
        details.put("nodeType", explain.reason());
        return new Finding(
                ID,
                SEVERITY,
                "Query performs a large table full scan",
                "Add a selective index or narrow the query predicate",
                explain.tableName(),
                null,
                fingerprint,
                details
        );
    }
}
