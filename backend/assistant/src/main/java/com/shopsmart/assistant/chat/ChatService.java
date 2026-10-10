package com.shopsmart.assistant.chat;

import com.shopsmart.assistant.agents.SupportTriageAgent;
import com.shopsmart.assistant.shopping.RecommendationService;
import com.shopsmart.kit.ai.AuditLogger;
import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.policy.PolicyAnswer;
import com.shopsmart.kit.policy.PolicyAnswerService;
import com.shopsmart.kit.security.CurrentUser;
import com.shopsmart.kit.web.Rows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One chat turn: store the message, route the intent, call the right tool, store and return the reply. */
@Service
public class ChatService {
    private static final Pattern DIGITS = Pattern.compile("#?(\\d{4,10})");

    private final JdbcClient jdbc;
    private final IntentRouter router;
    private final RecommendationService recommender;
    private final PolicyAnswerService policy;
    private final SupportTriageAgent triage;
    private final AuditLogger audit;

    public ChatService(JdbcClient jdbc, IntentRouter router, RecommendationService recommender, PolicyAnswerService policy,
                       SupportTriageAgent triage, AuditLogger audit) {
        this.jdbc = jdbc;
        this.router = router;
        this.recommender = recommender;
        this.policy = policy;
        this.triage = triage;
        this.audit = audit;
    }

    public Map<String, Object> chat(CurrentUser user, String sessionIdText, String message) {
        UUID sessionId = ensureSession(user.id(), sessionIdText, message);
        save(sessionId, "USER", message, null, null, null);

        long start = System.currentTimeMillis();
        String intent = router.route(message);
        audit.log(user.id(), sessionId, "INTENT_ROUTING", message, Map.of("intent", intent), null, Map.of("intent", intent),
                null, "OK", null, "intent-router", System.currentTimeMillis() - start);

        String text;
        Map<String, Object> payload = new LinkedHashMap<>();
        Long decisionId = null;
        switch (intent) {
            case IntentRouter.SHOPPING -> {
                var rec = recommender.recommend(message, user.id(), sessionId);
                decisionId = rec.decisionId();
                payload.put("recommendations", rec.recommendations());
                payload.put("interpretedRequirement", rec.interpretedRequirement());
                payload.put("clarifyingQuestion", rec.clarifyingQuestion());
                if (rec.recommendations().isEmpty()) {
                    text = rec.clarifyingQuestion();
                } else {
                    text = "Here are my picks for \"" + rec.interpretedRequirement() + "\"."
                            + (rec.clarifyingQuestion() == null ? "" : " " + rec.clarifyingQuestion());
                }
            }
            case IntentRouter.POLICY -> {
                PolicyAnswer a = policy.ask(message, user);
                payload.put("policy", a);
                text = a.answer();
            }
            case IntentRouter.ORDER -> {
                text = orderReply(user, message, payload);
                if (IntentRouter.POLICY_WORDS.matcher(message.toLowerCase()).find()) {
                    PolicyAnswer a = policy.ask(message, user);
                    if (a.sources() != null && !a.sources().isEmpty()) {
                        payload.put("policy", a);
                        text = text + "\n\nAbout your policy question: " + a.answer();
                    }
                }
            }
            case IntentRouter.SUPPORT -> {
                Map<String, Object> t = triage.triage(user.id(), "Customer support request", message, orderNumberIn(message));
                payload.put("ticket", t);
                text = "I'm sorry about that. I've opened support ticket #" + t.get("id") + " (priority " + t.get("priority")
                        + ") for our " + String.valueOf(t.get("team")).toLowerCase() + " team. They will follow up on your account email.";
            }
            default -> text = "Hi! I can recommend products, answer policy questions, track your orders or open a support ticket. What would you like to do?";
        }

        long msgId = save(sessionId, "ASSISTANT", text, intent, payload.isEmpty() ? null : payload, decisionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("messageId", msgId);
        out.put("intent", intent);
        out.put("text", text);
        out.put("payload", payload);
        return out;
    }

    // ----------------------------------------------------------------- order tracking tool

    private String orderReply(CurrentUser user, String message, Map<String, Object> payload) {
        long start = System.currentTimeMillis();
        String ref = orderNumberIn(message);
        List<Map<String, Object>> rows;
        if (ref != null) {
            rows = jdbc.sql(trackingSql() + " WHERE user_id = :u AND order_number = :n").param("u", user.id()).param("n", ref).query().listOfRows();
        } else {
            Matcher dm = DIGITS.matcher(message);
            if (dm.find()) {
                rows = jdbc.sql(trackingSql() + " WHERE user_id = :u AND order_number LIKE :n ORDER BY placed_at DESC LIMIT 1")
                        .param("u", user.id()).param("n", "%" + dm.group(1)).query().listOfRows();
            } else {
                rows = jdbc.sql(trackingSql() + " WHERE user_id = :u ORDER BY placed_at DESC LIMIT 1").param("u", user.id()).query().listOfRows();
            }
        }
        if (rows.isEmpty()) {
            audit.log(user.id(), null, "ORDER_TRACKING", message, Map.of("orderRef", String.valueOf(ref)), null, Map.of("found", false),
                    "LOW", "REFUSED", null, "order-tool", System.currentTimeMillis() - start);
            return "I couldn't find an order matching that on your account. Please check the order number (it looks like SS12345678) on your Orders page.";
        }
        Map<String, Object> t = rows.get(0);
        payload.put("tracking", t);
        StringBuilder sb = new StringBuilder();
        sb.append("Order ").append(t.get("orderNumber")).append(" is ").append(pretty(t.get("orderStatus"))).append('.');
        if (t.get("carrier") != null) {
            sb.append(" It ships with ").append(t.get("carrier")).append(" (tracking ").append(t.get("trackingNumber")).append(").");
        }
        if (t.get("lastEvent") != null) {
            sb.append(" Latest update: ").append(t.get("lastEvent"));
            if (t.get("lastLocation") != null) sb.append(" at ").append(t.get("lastLocation"));
            sb.append('.');
        }
        if (t.get("estimatedDelivery") != null && !"DELIVERED".equals(t.get("orderStatus")) && !"CANCELLED".equals(t.get("orderStatus"))) {
            sb.append(" Estimated delivery: ").append(t.get("estimatedDelivery")).append('.');
        }
        if (t.get("delayReason") != null) sb.append(" It is delayed: ").append(t.get("delayReason")).append('.');
        audit.log(user.id(), null, "ORDER_TRACKING", message, Map.of("orderRef", t.get("orderNumber")), List.of(t), Map.of("reply", sb.toString()),
                "HIGH", "OK", null, "order-tool", System.currentTimeMillis() - start);
        return sb.toString();
    }

    private static String trackingSql() {
        return """
                SELECT order_number AS "orderNumber", order_status AS "orderStatus", carrier, tracking_number AS "trackingNumber",
                       shipment_status AS "shipmentStatus", estimated_delivery::text AS "estimatedDelivery", delay_reason AS "delayReason",
                       last_event AS "lastEvent", last_location AS "lastLocation", placed_at AS "placedAt"
                FROM v_order_tracking""";
    }

    private static String pretty(Object status) { return String.valueOf(status).toLowerCase().replace('_', ' '); }

    static String orderNumberIn(String message) {
        Matcher m = IntentRouter.ORDER_REF.matcher(message == null ? "" : message);
        return m.find() ? m.group().toUpperCase() : null;
    }

    // ------------------------------------------------------------------- sessions

    private UUID ensureSession(long userId, String sessionIdText, String firstMessage) {
        if (sessionIdText != null && !sessionIdText.isBlank()) {
            UUID id;
            try {
                id = UUID.fromString(sessionIdText);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Invalid session id");
            }
            Integer own = jdbc.sql("SELECT count(*) FROM chat_sessions WHERE id = :id AND user_id = :u").param("id", id).param("u", userId)
                    .query(Integer.class).single();
            if (own == 0) throw ApiException.notFound("Chat session");
            return id;
        }
        String title = firstMessage.length() > 60 ? firstMessage.substring(0, 60) : firstMessage;
        return jdbc.sql("INSERT INTO chat_sessions (user_id, title) VALUES (:u, :t) RETURNING id").param("u", userId).param("t", title)
                .query(UUID.class).single();
    }

    private long save(UUID sessionId, String role, String content, String intent, Object payload, Long decisionId) {
        return jdbc.sql("""
                INSERT INTO chat_messages (session_id, role, content, intent, payload, decision_log_id)
                VALUES (:s, :r, :c, :i, CAST(:p AS jsonb), :d) RETURNING id
                """).param("s", sessionId).param("r", role).param("c", content).param("i", intent)
                .param("p", payload == null ? null : Rows.toJson(payload)).param("d", decisionId).query(Long.class).single();
    }

    public List<Map<String, Object>> sessions(long userId) {
        return jdbc.sql("SELECT id, title, created_at AS \"createdAt\" FROM chat_sessions WHERE user_id = :u ORDER BY created_at DESC LIMIT 30")
                .param("u", userId).query().listOfRows();
    }

    public List<Map<String, Object>> messages(long userId, UUID sessionId) {
        Integer own = jdbc.sql("SELECT count(*) FROM chat_sessions WHERE id = :id AND user_id = :u").param("id", sessionId).param("u", userId)
                .query(Integer.class).single();
        if (own == 0) throw ApiException.notFound("Chat session");
        return Rows.json(jdbc.sql("""
                SELECT id, role, content, intent, payload::text AS payload, created_at AS "createdAt"
                FROM chat_messages WHERE session_id = :s ORDER BY id
                """).param("s", sessionId).query().listOfRows(), "payload");
    }
}
