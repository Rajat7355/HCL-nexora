package com.shopsmart.assistant.shopping;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsmart.kit.ai.AuditLogger;
import com.shopsmart.kit.ai.LlmGateway;
import com.shopsmart.kit.web.Rows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SQL-first recommender: hard filters (category, budget, brand, stock) are applied in the database, ranking mixes
 * text relevance, spec matches, rating and the shopper's saved preferences. Reasons are built from real product
 * facts, so an explanation can never mention something the product does not have.
 */
@Service
public class RecommendationService {

    public record Recommendation(String productId, String productName, String reason, String availability,
                                 BigDecimal price, BigDecimal rating, String category, String imageUrl) {}

    public record RecommendResponse(String interpretedRequirement, Map<String, Object> budget, List<Recommendation> recommendations,
                                    String clarifyingQuestion, Map<String, Object> requirement, Long decisionId) {}

    private final JdbcClient jdbc;
    private final IntentExtractor extractor;
    private final AuditLogger audit;
    private final LlmGateway llm;

    public RecommendationService(JdbcClient jdbc, IntentExtractor extractor, AuditLogger audit, LlmGateway llm) {
        this.jdbc = jdbc;
        this.extractor = extractor;
        this.audit = audit;
        this.llm = llm;
    }

    public RecommendResponse recommend(String query, Long userId, UUID sessionId) {
        long start = System.currentTimeMillis();
        Requirement req = extractor.extract(query);

        // fall back to the saved budget when the message states none
        if (userId != null && req.budgetMin() == null && req.budgetMax() == null) {
            List<Map<String, Object>> prefs = jdbc.sql("SELECT budget_min AS mn, budget_max AS mx FROM customer_preferences WHERE user_id = :u")
                    .param("u", userId).query().listOfRows();
            if (!prefs.isEmpty() && (prefs.get(0).get("mn") != null || prefs.get(0).get("mx") != null)) {
                req = new Requirement(req.category(), req.categorySlug(), (BigDecimal) prefs.get(0).get("mn"), (BigDecimal) prefs.get(0).get("mx"),
                        req.brand(), req.keywords(), req.usage(), true);
            }
        }

        String clarifying = null;
        List<Recommendation> recs;
        if (req.categorySlug() == null && req.keywords().isEmpty() && req.brand() == null) {
            recs = List.of();
            clarifying = "What kind of product are you looking for (for example a laptop, smartphone or headphones), and what is your budget?";
        } else {
            recs = search(req, userId, true);
            if (recs.isEmpty() && (req.budgetMax() != null || req.budgetMin() != null)) {
                List<Recommendation> relaxed = search(req, userId, false);
                if (!relaxed.isEmpty()) {
                    BigDecimal cheapest = relaxed.stream().map(Recommendation::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    clarifying = "Nothing matched within your budget. The closest options start at ₹" + cheapest.stripTrailingZeros().toPlainString()
                            + ". Would you like to see those, or change your budget?";
                    recs = relaxed;
                }
            }
            if (recs.isEmpty() && clarifying == null) {
                clarifying = "I couldn't find a matching in-stock product. Could you describe what you need differently, or choose a category?";
            }
            if (clarifying == null && req.budgetMax() == null && req.budgetMin() == null) {
                clarifying = "What is your budget? I can narrow these down.";
            }
            if (clarifying == null && req.categorySlug() == null) {
                clarifying = "Which product category do you mean? That helps me pick better matches.";
            }
        }

        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("min", req.budgetMin());
        budget.put("max", req.budgetMax());
        budget.put("currency", "INR");
        budget.put("source", req.budgetFromPreferences() ? "saved preferences" : (req.budgetMin() != null || req.budgetMax() != null ? "your message" : "not specified"));

        Map<String, Object> reqMap = new LinkedHashMap<>();
        reqMap.put("category", req.category());
        reqMap.put("brand", req.brand());
        reqMap.put("keywords", req.keywords());
        reqMap.put("usage", req.usage());

        List<Map<String, Object>> context = new ArrayList<>();
        for (Recommendation r : recs) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("productId", r.productId());
            c.put("price", r.price());
            c.put("availability", r.availability());
            context.add(c);
        }
        String summary = req.summary();
        Long logId = audit.log(userId, sessionId, "SHOPPING_RECOMMENDATION", query, reqMap, context,
                Map.of("interpretedRequirement", summary, "recommended", recs.stream().map(Recommendation::productId).toList(),
                        "clarifyingQuestion", clarifying == null ? "" : clarifying),
                recs.isEmpty() ? "LOW" : (clarifying == null ? "HIGH" : "MEDIUM"), recs.isEmpty() ? "REFUSED" : "OK", null,
                llm.modelName(), System.currentTimeMillis() - start);
        return new RecommendResponse(summary, budget, recs, clarifying, reqMap, logId);
    }

    List<Recommendation> search(Requirement req, Long userId, boolean applyBudget) {
        StringBuilder score = new StringBuilder("(p.rating / 5.0)");
        StringBuilder where = new StringBuilder(" WHERE p.is_active AND a.available_qty > 0 ");
        Map<String, Object> params = new LinkedHashMap<>();

        List<String> kws = req.keywords();
        for (int i = 0; i < kws.size(); i++) {
            String key = "k" + i;
            params.put(key, "%" + kws.get(i) + "%");
            score.append(" + (CASE WHEN p.name ILIKE :").append(key).append(" OR p.specs::text ILIKE :").append(key)
                 .append(" OR p.description ILIKE :").append(key).append(" THEN 0.6 ELSE 0 END)");
        }
        if (!kws.isEmpty()) {
            StringBuilder tsq = new StringBuilder();
            for (String k : kws) {
                String clean = k.replaceAll("[^a-z0-9]", "");
                if (clean.isEmpty()) continue;
                if (tsq.length() > 0) tsq.append(" | ");
                tsq.append(clean);
            }
            if (tsq.length() > 0) {
                params.put("tsq", tsq.toString());
                score.append(" + COALESCE(ts_rank(p.search_vector, to_tsquery('english', :tsq)), 0) * 2");
            }
        }
        if (req.categorySlug() != null) {
            params.put("cat", req.categorySlug());
            where.append(" AND p.category_id IN (SELECT id FROM categories WHERE slug = :cat OR parent_id = (SELECT id FROM categories WHERE slug = :cat))");
        }
        if (req.brand() != null) {
            params.put("brand", req.brand());
            where.append(" AND b.name = :brand");
        }
        if (applyBudget) {
            if (req.budgetMax() != null) { params.put("bmax", req.budgetMax()); where.append(" AND p.price <= :bmax"); }
            if (req.budgetMin() != null) { params.put("bmin", req.budgetMin()); where.append(" AND p.price >= :bmin"); }
        }
        if (userId != null) {
            params.put("uid", userId);
            score.append(" + (CASE WHEN EXISTS (SELECT 1 FROM user_preferred_categories u WHERE u.user_id = :uid AND u.category_id = p.category_id) THEN 0.4 ELSE 0 END)")
                 .append(" + (CASE WHEN EXISTS (SELECT 1 FROM user_preferred_brands u WHERE u.user_id = :uid AND u.brand_id = p.brand_id) THEN 0.3 ELSE 0 END)");
        }
        // when the budget was not applied, show the cheapest options first
        String order = applyBudget ? "score DESC, p.rating DESC, p.price ASC" : "p.price ASC, score DESC";

        List<Map<String, Object>> rows = jdbc.sql("SELECT p.sku, p.name, b.name AS brand, c.name AS category, p.price, p.rating, p.rating_count AS rc, "
                + "p.specs::text AS specs, a.available_qty AS qty, a.availability, "
                + "(SELECT i.url FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.sort_order LIMIT 1) AS img, "
                + score + " AS score "
                + "FROM products p JOIN brands b ON b.id = p.brand_id JOIN categories c ON c.id = p.category_id "
                + "JOIN v_product_availability a ON a.product_id = p.id" + where + " ORDER BY " + order + " LIMIT 4")
                .params(params).query().listOfRows();

        List<Recommendation> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(new Recommendation((String) r.get("sku"), (String) r.get("name"), reason(req, r), (String) r.get("availability"),
                    (BigDecimal) r.get("price"), (BigDecimal) r.get("rating"), (String) r.get("category"), (String) r.get("img")));
        }
        return out;
    }

    /** Reason text built only from facts in the database row. */
    String reason(Requirement req, Map<String, Object> r) {
        List<String> parts = new ArrayList<>();
        BigDecimal price = (BigDecimal) r.get("price");
        String p = "₹" + price.stripTrailingZeros().toPlainString();
        if (req.budgetMax() != null && price.compareTo(req.budgetMax()) <= 0) {
            parts.add("fits your ₹" + req.budgetMax().stripTrailingZeros().toPlainString() + " budget at " + p);
        } else if (req.budgetMax() != null) {
            parts.add("priced at " + p + ", above your ₹" + req.budgetMax().stripTrailingZeros().toPlainString() + " budget");
        } else {
            parts.add("priced at " + p);
        }
        BigDecimal rating = (BigDecimal) r.get("rating");
        int rc = ((Number) r.get("rc")).intValue();
        if (rating.compareTo(BigDecimal.ZERO) > 0) parts.add("rated " + rating + "/5" + (rc > 0 ? " by " + rc + " buyers" : ""));

        JsonNode specs = Rows.parse(r.get("specs"));
        List<String> matched = new ArrayList<>();
        if (specs != null) {
            String name = ((String) r.get("name")).toLowerCase(Locale.ROOT);
            for (String k : req.keywords()) {
                var it = specs.fields();
                boolean found = false;
                while (it.hasNext()) {
                    var e = it.next();
                    String val = e.getValue().asText();
                    if ((e.getKey() + " " + val).toLowerCase(Locale.ROOT).contains(k)) {
                        matched.add(e.getKey().replace('_', ' ') + " " + val);
                        found = true;
                        break;
                    }
                }
                if (!found && name.contains(k)) matched.add("matches \"" + k + "\"");
                if (matched.size() >= 3) break;
            }
        }
        if (!matched.isEmpty()) parts.add("has " + String.join(", ", matched));
        int qty = ((Number) r.get("qty")).intValue();
        parts.add(qty <= 10 ? "only " + qty + " left in stock" : "in stock");
        String first = parts.get(0);
        parts.set(0, Character.toUpperCase(first.charAt(0)) + first.substring(1));
        return String.join("; ", parts) + ".";
    }
}
