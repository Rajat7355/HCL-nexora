package com.shopsmart.assistant.agents;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The three ops agents. Each is a short, bounded plan over allow-listed tools; every step is logged. */
@Component
public class Agents {
    static final Set<String> INVENTORY_TOOLS = Set.of("get_low_cover_products", "forecast_stockout", "create_stock_alerts", "propose_restock");
    static final Set<String> FULFILMENT_TOOLS = Set.of("find_delayed_shipments", "draft_customer_messages", "create_delay_tickets");
    static final Set<String> TRIAGE_TOOLS = Set.of("classify_ticket", "create_support_ticket");

    private final AgentRunner runner;

    public Agents(AgentRunner runner, AgentTools tools) {
        this.runner = runner; // AgentTools is injected so its tools are registered before any run
    }

    @SuppressWarnings("unchecked")
    public long runInventory(String trigger) {
        long run = runner.start("INVENTORY", trigger, "Find products likely to stock out and propose restocks for approval", true);
        try {
            var low = runner.call(run, INVENTORY_TOOLS, "Read 30 day sales velocity and find products with under 14 days of cover or at the reorder level",
                    "get_low_cover_products", Map.of());
            List<Map<String, Object>> items = (List<Map<String, Object>>) low.get("items");
            if (items.isEmpty()) {
                runner.finish(run, "COMPLETED", "No products are at risk of stocking out.");
                return run;
            }
            var forecast = runner.call(run, INVENTORY_TOOLS, "Estimate stock-out dates and severity from average daily sales",
                    "forecast_stockout", Map.of("items", items));
            Map<String, Object> alertsInput = new LinkedHashMap<>();
            alertsInput.put("items", forecast.get("items"));
            alertsInput.put("runId", run);
            var alerts = runner.call(run, INVENTORY_TOOLS, "Raise one alert per at-risk product and warehouse, skipping ones already open",
                    "create_stock_alerts", alertsInput);
            runner.call(run, INVENTORY_TOOLS, "Prepare a restock proposal for a human to approve; the agent never changes stock itself",
                    "propose_restock", Map.of("items", forecast.get("items")));
            runner.finish(run, "AWAITING_APPROVAL", items.size() + " at-risk product(s) found; " + alerts.get("created")
                    + " new alert(s) raised. Waiting for staff approval of the restock proposal.");
        } catch (RuntimeException e) {
            runner.finish(run, "FAILED", "Run stopped: " + e.getMessage());
        }
        return run;
    }

    @SuppressWarnings("unchecked")
    public long runFulfilment(String trigger) {
        long run = runner.start("FULFILLMENT", trigger, "Find delayed shipments, draft customer updates and open logistics tickets", false);
        try {
            var found = runner.call(run, FULFILMENT_TOOLS, "Look at the tracking view for delayed, exception or overdue shipments",
                    "find_delayed_shipments", Map.of());
            List<Map<String, Object>> items = (List<Map<String, Object>>) found.get("items");
            if (items.isEmpty()) {
                runner.finish(run, "COMPLETED", "No delayed shipments found.");
                return run;
            }
            var drafted = runner.call(run, FULFILMENT_TOOLS, "Draft a clear delay notice for each affected customer",
                    "draft_customer_messages", Map.of("items", items));
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("items", drafted.get("items"));
            in.put("runId", run);
            var tickets = runner.call(run, FULFILMENT_TOOLS, "Open one logistics ticket per delayed order, skipping orders that already have one",
                    "create_delay_tickets", in);
            runner.finish(run, "COMPLETED", items.size() + " delayed shipment(s) found; " + tickets.get("created") + " ticket(s) opened.");
        } catch (RuntimeException e) {
            runner.finish(run, "FAILED", "Run stopped: " + e.getMessage());
        }
        return run;
    }
}
