package com.shopsmart.assistant.agents;

import com.shopsmart.kit.ai.AuditLogger;
import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.Rows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded tool loop shared by all agents: only allow-listed tools can be called, there is a hard step limit,
 * and every step (reasoning, input, output) is stored in agent_steps and finally summarised in the audit log.
 */
@Component
public class AgentRunner {
    public static final int MAX_STEPS = 8;

    @FunctionalInterface
    public interface Tool {
        Map<String, Object> run(Map<String, Object> input);
    }

    private final JdbcClient jdbc;
    private final AuditLogger audit;
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public AgentRunner(JdbcClient jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public void register(String name, Tool tool) { tools.put(name, tool); }

    public long start(String agentType, String trigger, String goal, boolean requiresApproval) {
        return jdbc.sql("""
                INSERT INTO agent_runs (agent_type, trigger_type, goal, status, requires_approval)
                VALUES (:t, :tr, :g, 'RUNNING', :ra) RETURNING id
                """).param("t", agentType).param("tr", trigger).param("g", goal).param("ra", requiresApproval).query(Long.class).single();
    }

    /** Runs one allow-listed tool as the next step of the run. */
    public Map<String, Object> call(long runId, Set<String> allowed, String reasoning, String toolName, Map<String, Object> input) {
        Integer used = jdbc.sql("SELECT count(*) FROM agent_steps WHERE run_id = :r").param("r", runId).query(Integer.class).single();
        if (used >= MAX_STEPS) throw new IllegalStateException("Step limit of " + MAX_STEPS + " reached");
        Tool tool = tools.get(toolName);
        if (!allowed.contains(toolName) || tool == null) {
            record(runId, toolName, reasoning, input, Map.of("error", "Tool not allowed for this agent"), "ERROR");
            throw new IllegalStateException("Tool not allowed: " + toolName);
        }
        try {
            Map<String, Object> out = tool.run(input);
            record(runId, toolName, reasoning, input, out, "OK");
            return out;
        } catch (RuntimeException e) {
            record(runId, toolName, reasoning, input, Map.of("error", String.valueOf(e.getMessage())), "ERROR");
            throw e;
        }
    }

    private void record(long runId, String tool, String reasoning, Map<String, Object> input, Map<String, Object> output, String status) {
        jdbc.sql("""
                INSERT INTO agent_steps (run_id, step_no, tool_name, reasoning, tool_input, tool_output, status)
                VALUES (:r, (SELECT COALESCE(max(step_no), 0) + 1 FROM agent_steps WHERE run_id = :r), :t, :why,
                        CAST(:in AS jsonb), CAST(:out AS jsonb), :st)
                """).param("r", runId).param("t", tool).param("why", reasoning)
                .param("in", Rows.toJson(trim(input))).param("out", Rows.toJson(trim(output))).param("st", status).update();
    }

    /** Keeps stored step payloads small. */
    private static Map<String, Object> trim(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List<?> list && list.size() > 15) v = list.subList(0, 15);
            out.put(e.getKey(), v);
        }
        return out;
    }

    public void finish(long runId, String status, String summary) {
        jdbc.sql("UPDATE agent_runs SET status = :s, result_summary = :m, finished_at = CASE WHEN :s = 'AWAITING_APPROVAL' THEN NULL ELSE now() END WHERE id = :id")
                .param("s", status).param("m", summary).param("id", runId).update();
        List<Map<String, Object>> steps = jdbc.sql("SELECT step_no AS n, tool_name AS tool, status FROM agent_steps WHERE run_id = :r ORDER BY step_no")
                .param("r", runId).query().listOfRows();
        String type = jdbc.sql("SELECT agent_type FROM agent_runs WHERE id = :id").param("id", runId).query(String.class).single();
        audit.log(null, null, "AGENT_DECISION", type + " agent run #" + runId, Map.of("runId", runId, "agent", type), steps,
                Map.of("status", status, "summary", summary == null ? "" : summary), "HIGH", status.equals("FAILED") ? "ERROR" : "OK",
                null, "agent-runner", 0);
    }

    // ------------------------------------------------------------------ admin reads / decisions

    public List<Map<String, Object>> runs(int limit) {
        return jdbc.sql("""
                SELECT id, agent_type AS "agentType", trigger_type AS trigger, goal, status, result_summary AS summary,
                       requires_approval AS "requiresApproval", started_at AS "startedAt", finished_at AS "finishedAt",
                       (SELECT count(*) FROM agent_steps s WHERE s.run_id = agent_runs.id) AS "stepCount"
                FROM agent_runs ORDER BY id DESC LIMIT :l
                """).param("l", Math.min(Math.max(limit, 1), 100)).query().listOfRows();
    }

    public Map<String, Object> run(long id) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT id, agent_type AS "agentType", trigger_type AS trigger, goal, status, result_summary AS summary,
                       requires_approval AS "requiresApproval", approved_by AS "approvedBy", approved_at AS "approvedAt",
                       started_at AS "startedAt", finished_at AS "finishedAt"
                FROM agent_runs WHERE id = :id
                """).param("id", id).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Agent run");
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        out.put("steps", Rows.json(jdbc.sql("""
                SELECT step_no AS "stepNo", tool_name AS tool, reasoning, tool_input::text AS input, tool_output::text AS output,
                       status, created_at AS "createdAt"
                FROM agent_steps WHERE run_id = :id ORDER BY step_no
                """).param("id", id).query().listOfRows(), "input", "output"));
        return out;
    }

    @Transactional
    public Map<String, Object> decide(long runId, long staffId, boolean approve) {
        String status = jdbc.sql("SELECT status FROM agent_runs WHERE id = :id FOR UPDATE").param("id", runId)
                .query(String.class).optional().orElseThrow(() -> ApiException.notFound("Agent run"));
        if (!status.equals("AWAITING_APPROVAL")) {
            throw ApiException.conflict("NOT_AWAITING_APPROVAL", "This run is not waiting for approval");
        }
        if (approve) {
            jdbc.sql("UPDATE agent_runs SET status = 'COMPLETED', approved_by = :u, approved_at = now(), finished_at = now() WHERE id = :id")
                    .param("u", staffId).param("id", runId).update();
            jdbc.sql("UPDATE stock_alerts SET status = 'ACKNOWLEDGED' WHERE agent_run_id = :id AND status = 'OPEN'").param("id", runId).update();
        } else {
            jdbc.sql("UPDATE agent_runs SET status = 'REJECTED', approved_by = :u, approved_at = now(), finished_at = now() WHERE id = :id")
                    .param("u", staffId).param("id", runId).update();
        }
        return run(runId);
    }
}
