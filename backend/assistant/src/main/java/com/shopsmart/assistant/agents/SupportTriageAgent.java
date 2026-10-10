package com.shopsmart.assistant.agents;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Classifies a customer problem, routes it to the right team and creates the ticket. */
@Component
public class SupportTriageAgent {
    private final AgentRunner runner;

    public SupportTriageAgent(AgentRunner runner, AgentTools tools) {
        this.runner = runner;
    }

    public Map<String, Object> triage(Long userId, String subject, String description, String orderNumber) {
        long run = runner.start("SUPPORT_TRIAGE", "EVENT", "Triage a customer support message", false);
        try {
            var cls = runner.call(run, Agents.TRIAGE_TOOLS, "Decide category, priority and owning team from the message",
                    "classify_ticket", Map.of("text", subject + ". " + description));
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("userId", userId);
            in.put("orderNumber", orderNumber);
            in.put("subject", subject.length() > 200 ? subject.substring(0, 200) : subject);
            in.put("description", description);
            in.put("category", cls.get("category"));
            in.put("priority", cls.get("priority"));
            in.put("team", cls.get("team"));
            in.put("runId", run);
            var ticket = runner.call(run, Agents.TRIAGE_TOOLS, "Create the ticket for the chosen team", "create_support_ticket", in);
            runner.finish(run, "COMPLETED", "Ticket #" + ticket.get("id") + " created for " + cls.get("team") + " (" + cls.get("priority") + ").");
            return ticket;
        } catch (RuntimeException e) {
            runner.finish(run, "FAILED", "Run stopped: " + e.getMessage());
            throw e;
        }
    }
}
