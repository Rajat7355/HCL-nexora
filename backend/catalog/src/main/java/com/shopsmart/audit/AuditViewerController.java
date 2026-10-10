package com.shopsmart.audit;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.PageResponse;
import com.shopsmart.kit.web.Rows;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Staff-only audit viewer: why did the AI answer that? Shows input, interpreted request, context, output, model. */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditViewerController {
    private final JdbcClient jdbc;

    public AuditViewerController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(required = false) String taskType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        int pageNo = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new LinkedHashMap<>();
        if (taskType != null && !taskType.isBlank()) { params.put("task", taskType); where.append(" AND l.task_type = :task"); }
        if (status != null && !status.isBlank()) { params.put("status", status); where.append(" AND l.status = :status"); }
        if (userId != null) { params.put("uid", userId); where.append(" AND l.user_id = :uid"); }
        if (from != null) { params.put("from", from.toString()); where.append(" AND l.created_at >= CAST(:from AS date)"); }
        if (to != null) { params.put("to", to.toString()); where.append(" AND l.created_at < CAST(:to AS date) + 1"); }

        Long total = jdbc.sql("SELECT count(*) FROM ai_decision_logs l " + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("lim", pageSize);
        pageParams.put("off", pageNo * pageSize);
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT l.id, l.user_id AS "userId", l.task_type AS "taskType", left(l.input_text, 160) AS input,
                       l.confidence, l.status, l.model_name AS model, l.latency_ms AS "latencyMs", l.created_at AS "createdAt"
                FROM ai_decision_logs l
                """ + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT :lim OFFSET :off").params(pageParams).query().listOfRows();
        return PageResponse.of(rows, pageNo, pageSize, total);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT l.id, l.user_id AS "userId", l.session_id AS "sessionId", l.task_type AS "taskType",
                       l.input_text AS input, l.interpreted::text AS interpreted, l.retrieved_context::text AS "retrievedContext",
                       l.output::text AS output, l.confidence, l.status, l.error_message AS error, l.model_name AS model,
                       l.prompt_tokens AS "promptTokens", l.completion_tokens AS "completionTokens",
                       l.latency_ms AS "latencyMs", l.created_at AS "createdAt"
                FROM ai_decision_logs l WHERE l.id = :id
                """).param("id", id).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Audit record");
        return Rows.json(rows.get(0), "interpreted", "retrievedContext", "output");
    }
}
