package com.shopsmart.kit.ai;

import com.shopsmart.kit.web.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The only writer of ai_decision_logs. Every AI answer, recommendation, routing decision and agent
 * decision goes through here, so the audit viewer can show input, context, output and model.
 */
@Component
public class AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private final JdbcClient jdbc;

    public AuditLogger(JdbcClient jdbc) { this.jdbc = jdbc; }

    /**
     * @param taskType   SHOPPING_RECOMMENDATION | RAG_ANSWER | ORDER_TRACKING | INTENT_ROUTING | AGENT_DECISION
     * @param confidence HIGH | MEDIUM | LOW or null
     * @param status     OK | REFUSED | ERROR
     * @return the new row id, or null if logging failed (logging never breaks the user flow)
     */
    public Long log(Long userId, UUID sessionId, String taskType, String input, Object interpreted,
                    Object context, Object output, String confidence, String status, String error,
                    String model, long latencyMs) {
        try {
            return jdbc.sql("""
                    INSERT INTO ai_decision_logs
                      (user_id, session_id, task_type, input_text, interpreted, retrieved_context, output,
                       confidence, status, error_message, model_name, latency_ms)
                    VALUES (:uid, :sid, :task, :input, CAST(:interp AS jsonb), CAST(:ctx AS jsonb), CAST(:out AS jsonb),
                            :conf, :status, :err, :model, :lat)
                    RETURNING id
                    """)
                    .param("uid", userId)
                    .param("sid", sessionId)
                    .param("task", taskType)
                    .param("input", input == null ? "" : input)
                    .param("interp", interpreted == null ? null : Rows.toJson(interpreted))
                    .param("ctx", context == null ? null : Rows.toJson(context))
                    .param("out", output == null ? null : Rows.toJson(output))
                    .param("conf", confidence)
                    .param("status", status == null ? "OK" : status)
                    .param("err", error)
                    .param("model", model)
                    .param("lat", (int) Math.min(latencyMs, Integer.MAX_VALUE))
                    .query(Long.class)
                    .single();
        } catch (Exception e) {
            log.error("Could not write audit log: {}", e.getMessage());
            return null;
        }
    }
}
