package com.shopsmart.assistant.agents;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Agent runs, stock alerts and tickets: staff views plus the customer ticket endpoints. */
@RestController
@RequestMapping("/api")
public class OpsController {
    private final AgentRunner runner;
    private final Agents agents;
    private final SupportTriageAgent triage;
    private final JdbcClient jdbc;

    public OpsController(AgentRunner runner, Agents agents, SupportTriageAgent triage, JdbcClient jdbc) {
        this.runner = runner;
        this.agents = agents;
        this.triage = triage;
        this.jdbc = jdbc;
    }

    public record TicketRequest(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 4000) String description,
                                @Size(max = 20) String orderNumber) {}

    public record StatusRequest(String status) {}

    // ------------------------------------------------------------------ agents (staff)

    @GetMapping("/admin/agents/runs")
    public List<Map<String, Object>> runs(@RequestParam(defaultValue = "30") int limit) { return runner.runs(limit); }

    @GetMapping("/admin/agents/runs/{id}")
    public Map<String, Object> run(@PathVariable long id) { return runner.run(id); }

    @PostMapping("/admin/agents/{type}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> start(@PathVariable String type) {
        long id;
        switch (type.toLowerCase()) {
            case "inventory" -> id = agents.runInventory("MANUAL");
            case "fulfillment", "fulfilment" -> id = agents.runFulfilment("MANUAL");
            default -> throw ApiException.badRequest("Unknown agent '" + type + "'. Use inventory or fulfillment");
        }
        return runner.run(id);
    }

    @PostMapping("/admin/agents/runs/{id}/approve")
    public Map<String, Object> approve(@AuthenticationPrincipal CurrentUser staff, @PathVariable long id) {
        return runner.decide(id, staff.id(), true);
    }

    @PostMapping("/admin/agents/runs/{id}/reject")
    public Map<String, Object> reject(@AuthenticationPrincipal CurrentUser staff, @PathVariable long id) {
        return runner.decide(id, staff.id(), false);
    }

    // ------------------------------------------------------------------ stock alerts (staff)

    @GetMapping("/admin/alerts")
    public List<Map<String, Object>> alerts(@RequestParam(required = false) String status) {
        String where = status == null || status.isBlank() ? "" : " WHERE a.status = :s";
        JdbcClient.StatementSpec spec = jdbc.sql("""
                SELECT a.id, p.sku AS "productId", p.name, w.name AS warehouse, a.severity, a.days_of_cover AS "daysOfCover",
                       a.predicted_stockout_date::text AS "predictedStockoutDate", a.recommended_action AS "recommendedAction",
                       a.status, a.agent_run_id AS "agentRunId", a.created_at AS "createdAt"
                FROM stock_alerts a JOIN products p ON p.id = a.product_id LEFT JOIN warehouses w ON w.id = a.warehouse_id
                """ + where + " ORDER BY CASE a.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END, a.created_at DESC LIMIT 100");
        if (!where.isEmpty()) spec = spec.param("s", status);
        return spec.query().listOfRows();
    }

    @PatchMapping("/admin/alerts/{id}")
    public Map<String, Object> updateAlert(@PathVariable long id, @RequestBody StatusRequest r) {
        if (r.status() == null || !Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED").contains(r.status())) {
            throw ApiException.badRequest("Status must be OPEN, ACKNOWLEDGED or RESOLVED");
        }
        int n = jdbc.sql("UPDATE stock_alerts SET status = :s, resolved_at = CASE WHEN :s = 'RESOLVED' THEN now() ELSE NULL END WHERE id = :id")
                .param("s", r.status()).param("id", id).update();
        if (n == 0) throw ApiException.notFound("Alert");
        return Map.of("id", id, "status", r.status());
    }

    // ------------------------------------------------------------------ tickets

    @GetMapping("/admin/tickets")
    public List<Map<String, Object>> tickets(@RequestParam(required = false) String status) {
        String where = status == null || status.isBlank() ? "" : " WHERE t.status = :s";
        JdbcClient.StatementSpec spec = jdbc.sql("""
                SELECT t.id, t.subject, t.description, t.category, t.priority, t.team, t.status, u.email::text AS "customerEmail",
                       o.order_number AS "orderNumber", t.agent_run_id AS "agentRunId", t.created_at AS "createdAt", t.resolved_at AS "resolvedAt"
                FROM support_tickets t LEFT JOIN users u ON u.id = t.user_id LEFT JOIN orders o ON o.id = t.order_id
                """ + where + " ORDER BY CASE t.priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END, t.created_at DESC LIMIT 100");
        if (!where.isEmpty()) spec = spec.param("s", status);
        return spec.query().listOfRows();
    }

    @PatchMapping("/admin/tickets/{id}")
    public Map<String, Object> updateTicket(@AuthenticationPrincipal CurrentUser staff, @PathVariable long id, @RequestBody StatusRequest r) {
        if (r.status() == null || !Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(r.status())) {
            throw ApiException.badRequest("Status must be OPEN, IN_PROGRESS, RESOLVED or CLOSED");
        }
        int n = jdbc.sql("""
                UPDATE support_tickets SET status = :s, assigned_to = COALESCE(assigned_to, :u),
                       resolved_at = CASE WHEN :s IN ('RESOLVED','CLOSED') THEN now() ELSE NULL END
                WHERE id = :id
                """).param("s", r.status()).param("u", staff.id()).param("id", id).update();
        if (n == 0) throw ApiException.notFound("Ticket");
        return Map.of("id", id, "status", r.status());
    }

    /** Customer: open a ticket. The triage agent classifies it and picks the team. */
    @PostMapping("/support/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TicketRequest r) {
        return triage.triage(user.id(), r.subject(), r.description(), r.orderNumber());
    }

    @GetMapping("/support/tickets")
    public List<Map<String, Object>> mine(@AuthenticationPrincipal CurrentUser user) {
        return jdbc.sql("""
                SELECT t.id, t.subject, t.category, t.priority, t.team, t.status, o.order_number AS "orderNumber", t.created_at AS "createdAt"
                FROM support_tickets t LEFT JOIN orders o ON o.id = t.order_id WHERE t.user_id = :u ORDER BY t.created_at DESC
                """).param("u", user.id()).query().listOfRows();
    }
}
