package com.shopsmart.assistant.agents;

import com.shopsmart.kit.ai.LlmGateway;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The tools agents may call. Reads use tables and views of other slices (never writes to them);
 * writes go only to this slice's own tables: stock_alerts and support_tickets.
 */
@Component
public class AgentTools {
    private static final Set<String> CATEGORIES = Set.of("DELIVERY", "RETURN_REFUND", "PRODUCT", "ACCOUNT", "OTHER");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Pattern URGENT = Pattern.compile("\\b(urgent|immediately|fraud|charged twice|stolen|unsafe|fire|burning|emergency)\\b");
    private static final Pattern HIGH = Pattern.compile("\\b(damaged|broken|defective|not received|never arrived|missing|angry|worst|lost|scam|wrong item)\\b");
    private static final Pattern DELIVERY_WORDS = Pattern.compile("\\b(deliver|delivery|shipping|courier|late|delay|delayed|arrive|arrived|tracking|parcel|package)\\b");
    private static final Pattern REFUND_WORDS = Pattern.compile("\\b(return|refund|exchange|money back|cancel)\\b");
    private static final Pattern PRODUCT_WORDS = Pattern.compile("\\b(broken|defective|damaged|not working|wrong item|faulty|quality|stopped working)\\b");
    private static final Pattern ACCOUNT_WORDS = Pattern.compile("\\b(login|password|account|email|membership|payment)\\b");
    private static final Pattern LOW = Pattern.compile("\\b(question|how do|feedback|suggestion|info|information)\\b");

    public record TicketClass(String category, String priority) {}

    private final JdbcClient jdbc;
    private final AgentRunner runner;
    private final LlmGateway llm;

    public AgentTools(JdbcClient jdbc, AgentRunner runner, LlmGateway llm) {
        this.jdbc = jdbc;
        this.runner = runner;
        this.llm = llm;
    }

    @PostConstruct
    void register() {
        runner.register("get_low_cover_products", this::lowCover);
        runner.register("forecast_stockout", this::forecast);
        runner.register("create_stock_alerts", this::createAlerts);
        runner.register("propose_restock", this::proposeRestock);
        runner.register("find_delayed_shipments", this::delayedShipments);
        runner.register("draft_customer_messages", this::draftMessages);
        runner.register("create_delay_tickets", this::createDelayTickets);
        runner.register("classify_ticket", this::classifyTicket);
        runner.register("create_support_ticket", this::createTicket);
    }

    // ---------------------------------------------------------------- inventory agent tools

    Map<String, Object> lowCover(Map<String, Object> in) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT p.id AS "pid", p.sku AS "productId", p.name, w.id AS "warehouseId", w.name AS warehouse,
                       v.quantity_on_hand AS "onHand", i.quantity_reserved AS reserved, i.reorder_level AS "reorderLevel",
                       v.avg_daily_sales AS "avgDailySales", v.days_of_cover AS "daysOfCover", v.units_sold_30d AS "unitsSold30d"
                FROM v_sales_velocity v
                JOIN inventory i ON i.product_id = v.product_id AND i.warehouse_id = v.warehouse_id
                JOIN products p ON p.id = v.product_id
                JOIN warehouses w ON w.id = v.warehouse_id
                WHERE p.is_active
                  AND ((v.days_of_cover IS NOT NULL AND v.days_of_cover < 14)
                       OR (i.quantity_on_hand - i.quantity_reserved) <= i.reorder_level)
                ORDER BY v.days_of_cover NULLS LAST, (i.quantity_on_hand - i.quantity_reserved)
                LIMIT 15
                """).query().listOfRows();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("avgDailySales", dbl(r.get("avgDailySales")));
            m.put("daysOfCover", dbl(r.get("daysOfCover")));
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", items.size());
        out.put("items", items);
        return out;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> forecast(Map<String, Object> in) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.getOrDefault("items", List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Map<String, Object> m = new LinkedHashMap<>(it);
            Double cover = dbl(it.get("daysOfCover"));
            int onHand = ((Number) it.get("onHand")).intValue();
            int reserved = ((Number) it.getOrDefault("reserved", 0)).intValue();
            int reorder = ((Number) it.get("reorderLevel")).intValue();
            double daily = it.get("avgDailySales") == null ? 0 : ((Number) it.get("avgDailySales")).doubleValue();
            String severity;
            if (onHand - reserved <= 0 || (cover != null && cover < 3)) severity = "CRITICAL";
            else if (cover != null && cover < 7) severity = "HIGH";
            else if (cover != null && cover < 14) severity = "MEDIUM";
            else severity = "LOW";
            m.put("severity", severity);
            m.put("stockoutDate", cover == null ? null : LocalDate.now().plusDays((long) Math.floor(cover)).toString());
            int target = (int) Math.ceil(daily * 30);
            m.put("suggestedQty", Math.max(Math.max(reorder * 2, target - onHand), 10));
            out.add(m);
        }
        return Map.of("items", out);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> createAlerts(Map<String, Object> in) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.getOrDefault("items", List.of());
        long runId = ((Number) in.get("runId")).longValue();
        int created = 0;
        int skipped = 0;
        for (Map<String, Object> it : items) {
            long pid = ((Number) it.get("pid")).longValue();
            long wid = ((Number) it.get("warehouseId")).longValue();
            Integer open = jdbc.sql("SELECT count(*) FROM stock_alerts WHERE product_id = :p AND warehouse_id = :w AND status = 'OPEN'")
                    .param("p", pid).param("w", wid).query(Integer.class).single();
            if (open > 0) { skipped++; continue; }
            Double cover = dbl(it.get("daysOfCover"));
            String action = "Reorder " + it.get("suggestedQty") + " units for " + it.get("warehouse") + ". "
                    + (cover == null ? "Stock is at or below the reorder level." : "About " + cover + " days of cover left at current sales.");
            jdbc.sql("""
                    INSERT INTO stock_alerts (product_id, warehouse_id, severity, days_of_cover, predicted_stockout_date, recommended_action, agent_run_id)
                    VALUES (:p, :w, :sev, :cov, CAST(:d AS date), :act, :run)
                    """).param("p", pid).param("w", wid).param("sev", it.get("severity"))
                    .param("cov", cover == null ? null : BigDecimal.valueOf(cover)).param("d", it.get("stockoutDate"))
                    .param("act", action).param("run", runId).update();
            created++;
        }
        return Map.of("created", created, "skippedExisting", skipped);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> proposeRestock(Map<String, Object> in) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.getOrDefault("items", List.of());
        List<Map<String, Object>> proposal = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("productId", it.get("productId"));
            p.put("name", it.get("name"));
            p.put("warehouse", it.get("warehouse"));
            p.put("quantity", it.get("suggestedQty"));
            p.put("severity", it.get("severity"));
            proposal.add(p);
        }
        return Map.of("proposal", proposal, "note", "Approving acknowledges the alerts. Staff then restock from the Inventory page.");
    }

    // ---------------------------------------------------------------- fulfilment agent tools

    Map<String, Object> delayedShipments(Map<String, Object> in) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT order_id AS "orderId", order_number AS "orderNumber", user_id AS "userId", carrier, tracking_number AS "trackingNumber",
                       shipment_status AS "shipmentStatus", estimated_delivery::text AS "estimatedDelivery", delay_reason AS "delayReason",
                       last_event AS "lastEvent", (estimated_delivery < current_date) AS overdue
                FROM v_order_tracking
                WHERE order_status IN ('PLACED','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY')
                  AND (shipment_status IN ('DELAYED','EXCEPTION') OR estimated_delivery < current_date)
                ORDER BY estimated_delivery NULLS LAST LIMIT 20
                """).query().listOfRows();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) items.add(new LinkedHashMap<>(r));
        return Map.of("count", items.size(), "items", items);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> draftMessages(Map<String, Object> in) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.getOrDefault("items", List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Map<String, Object> m = new LinkedHashMap<>(it);
            String reason = it.get("delayReason") == null ? "a delay with the carrier" : String.valueOf(it.get("delayReason")).toLowerCase(Locale.ROOT);
            String eta = it.get("estimatedDelivery") == null ? "soon" : String.valueOf(it.get("estimatedDelivery"));
            m.put("message", "Your order " + it.get("orderNumber") + " is running late because of " + reason
                    + ". The new estimated delivery date is " + eta + ". We are sorry for the wait and are monitoring the shipment.");
            out.add(m);
        }
        return Map.of("items", out);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> createDelayTickets(Map<String, Object> in) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) in.getOrDefault("items", List.of());
        long runId = ((Number) in.get("runId")).longValue();
        int created = 0;
        int skipped = 0;
        for (Map<String, Object> it : items) {
            long orderId = ((Number) it.get("orderId")).longValue();
            Integer open = jdbc.sql("SELECT count(*) FROM support_tickets WHERE order_id = :o AND category = 'DELIVERY' AND status IN ('OPEN','IN_PROGRESS')")
                    .param("o", orderId).query(Integer.class).single();
            if (open > 0) { skipped++; continue; }
            boolean overdue = Boolean.TRUE.equals(it.get("overdue"));
            jdbc.sql("""
                    INSERT INTO support_tickets (user_id, order_id, subject, description, category, priority, team, agent_run_id)
                    VALUES (:u, :o, :s, :d, 'DELIVERY', :p, 'LOGISTICS', :run)
                    """).param("u", it.get("userId")).param("o", orderId).param("s", "Delayed delivery: order " + it.get("orderNumber"))
                    .param("d", "Carrier " + it.get("carrier") + ", tracking " + it.get("trackingNumber") + ". Status " + it.get("shipmentStatus")
                            + ". Draft customer message: " + it.get("message"))
                    .param("p", overdue ? "HIGH" : "MEDIUM").param("run", runId).update();
            created++;
        }
        return Map.of("created", created, "skippedExisting", skipped);
    }

    // ---------------------------------------------------------------- support triage tools

    Map<String, Object> classifyTicket(Map<String, Object> in) {
        String text = String.valueOf(in.getOrDefault("text", ""));
        String lower = text.toLowerCase(Locale.ROOT);
        String category = null;
        String priority = null;
        if (llm.available()) {
            var r = llm.completeJson("Classify this support message. Return JSON with category (one of DELIVERY, RETURN_REFUND, PRODUCT, ACCOUNT, OTHER) "
                    + "and priority (one of LOW, MEDIUM, HIGH, URGENT).", text, TicketClass.class);
            if (r.isPresent()) {
                if (r.get().category() != null && CATEGORIES.contains(r.get().category().toUpperCase(Locale.ROOT))) category = r.get().category().toUpperCase(Locale.ROOT);
                if (r.get().priority() != null && PRIORITIES.contains(r.get().priority().toUpperCase(Locale.ROOT))) priority = r.get().priority().toUpperCase(Locale.ROOT);
            }
        }
        if (category == null) {
            if (DELIVERY_WORDS.matcher(lower).find()) category = "DELIVERY";
            else if (REFUND_WORDS.matcher(lower).find()) category = "RETURN_REFUND";
            else if (PRODUCT_WORDS.matcher(lower).find()) category = "PRODUCT";
            else if (ACCOUNT_WORDS.matcher(lower).find()) category = "ACCOUNT";
            else category = "OTHER";
        }
        if (priority == null) {
            if (URGENT.matcher(lower).find()) priority = "URGENT";
            else if (HIGH.matcher(lower).find()) priority = "HIGH";
            else if (LOW.matcher(lower).find()) priority = "LOW";
            else priority = "MEDIUM";
        }
        String team = category.equals("DELIVERY") ? "LOGISTICS" : "SUPPORT";
        return Map.of("category", category, "priority", priority, "team", team);
    }

    Map<String, Object> createTicket(Map<String, Object> in) {
        Object userId = in.get("userId");
        Object orderNumber = in.get("orderNumber");
        Long orderId = null;
        if (orderNumber != null) {
            orderId = jdbc.sql("SELECT id FROM orders WHERE order_number = :n AND (CAST(:u AS bigint) IS NULL OR user_id = CAST(:u AS bigint))")
                    .param("n", orderNumber).param("u", userId).query(Long.class).optional().orElse(null);
        }
        Map<String, Object> row = jdbc.sql("""
                INSERT INTO support_tickets (user_id, order_id, subject, description, category, priority, team, agent_run_id)
                VALUES (:u, :o, :s, :d, :c, :p, :t, :run)
                RETURNING id, category, priority, team, status, subject
                """).param("u", userId).param("o", orderId).param("s", in.get("subject")).param("d", in.get("description"))
                .param("c", in.get("category")).param("p", in.get("priority")).param("t", in.get("team")).param("run", in.get("runId"))
                .query().singleRow();
        return new LinkedHashMap<>(row);
    }

    private static Double dbl(Object o) { return o == null ? null : ((Number) o).doubleValue(); }
}
