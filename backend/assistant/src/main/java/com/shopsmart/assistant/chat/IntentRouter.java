package com.shopsmart.assistant.chat;

import com.shopsmart.kit.ai.LlmGateway;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Decides whether a message is about shopping, policy, an order, support, or something else. */
@Component
public class IntentRouter {
    public static final String SHOPPING = "SHOPPING";
    public static final String POLICY = "POLICY";
    public static final String ORDER = "ORDER";
    public static final String SUPPORT = "SUPPORT";
    public static final String OTHER = "OTHER";

    public record IntentChoice(String intent) {}

    static final Pattern ORDER_REF = Pattern.compile("\\bSS\\d{8}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_WORDS = Pattern.compile(
            "\\b(track|tracking|where is|where's|status of|when will|my order|my parcel|my package|my shipment|has (it|my order) (shipped|arrived)|arrive|eta)\\b");
    private static final Pattern SUPPORT_WORDS = Pattern.compile(
            "\\b(damaged|broken|defective|faulty|not working|stopped working|wrong item|wrong product|missing item|complain|complaint|angry|disappointed|worst|scam|fraud|charged twice|never arrived|not received|lost my|talk to (a )?(human|agent|someone)|raise a ticket|support ticket)\\b");
    static final Pattern POLICY_WORDS = Pattern.compile(
            "\\b(policy|policies|return|returns|refund|refunds|exchange|warranty|guarantee|cancel|cancellation|membership|benefit|benefits|prime|premium|basic|shipping fee|delivery fee|free shipping|sla|compensation|coupon|terms|allowed|eligible|how long)\\b");
    private static final Pattern SHOPPING_WORDS = Pattern.compile(
            "\\b(buy|recommend|suggest|looking for|need a|need an|want a|want an|show me|find me|best|compare|under|below|budget|cheap|affordable|laptop|phone|smartphone|headphones?|earbuds|speaker|camera|blender|coffee|fitness|yoga|treadmill|watch)\\b");
    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "thanks", "thank you", "ok", "okay", "bye", "good morning", "good evening");

    private final LlmGateway llm;

    public IntentRouter(LlmGateway llm) { this.llm = llm; }

    public String route(String message) {
        String m = message == null ? "" : message.trim();
        if (llm.available()) {
            String system = "Classify the customer message into exactly one intent: SHOPPING (wants product suggestions or to buy), "
                    + "POLICY (asks about returns, refunds, shipping rules, warranty, membership), ORDER (asks where an order is or its status), "
                    + "SUPPORT (reports a problem such as damaged, wrong or missing items and needs help), OTHER (greeting or unrelated). "
                    + "Return JSON with a single field named intent.";
            var choice = llm.completeJson(system, m, IntentChoice.class);
            if (choice.isPresent() && choice.get().intent() != null) {
                String v = choice.get().intent().trim().toUpperCase(Locale.ROOT);
                if (Set.of(SHOPPING, POLICY, ORDER, SUPPORT, OTHER).contains(v)) return v;
            }
        }
        return rules(m);
    }

    static String rules(String message) {
        String m = message.toLowerCase(Locale.ROOT).trim();
        if (m.isEmpty() || GREETINGS.contains(m)) return OTHER;
        if (ORDER_REF.matcher(message).find() || ORDER_WORDS.matcher(m).find()) return ORDER;
        if (SUPPORT_WORDS.matcher(m).find()) return SUPPORT;
        if (POLICY_WORDS.matcher(m).find() && !SHOPPING_WORDS.matcher(m).find()) return POLICY;
        if (POLICY_WORDS.matcher(m).find() && m.contains("?") && !m.contains("recommend") && !m.contains("suggest")) return POLICY;
        if (SHOPPING_WORDS.matcher(m).find()) return SHOPPING;
        return m.length() > 12 ? SHOPPING : OTHER;
    }
}
