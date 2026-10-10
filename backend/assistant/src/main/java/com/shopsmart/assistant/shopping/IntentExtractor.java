package com.shopsmart.assistant.shopping;

import com.shopsmart.kit.ai.LlmGateway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free text into a validated {@link Requirement}. The LLM (Spring AI structured output) is used when
 * configured; rule-based extraction is the fallback and also cleans up whatever the model returns.
 */
@Component
public class IntentExtractor {

    /** Shape the model is asked to return. Everything is re-validated afterwards. */
    public record LlmRequirement(String category, Double budgetMin, Double budgetMax, String brand, List<String> keywords, String usage) {}

    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "laptops", List.of("laptop", "notebook", "macbook", "ultrabook", "chromebook"),
            "smartphones", List.of("phone", "smartphone", "mobile", "iphone", "android"),
            "headphones", List.of("headphone", "earbud", "earphone", "headset", "airpods", "earbuds"),
            "smart-home", List.of("smart home", "speaker", "camera", "bulb", "thermostat", "doorbell", "plug", "alexa", "security"),
            "fitness", List.of("fitness", "yoga", "dumbbell", "treadmill", "gym", "tracker", "workout", "running", "exercise"),
            "kitchen", List.of("kitchen", "blender", "coffee", "cooker", "fryer", "kettle", "toaster", "mixer", "cooking"));

    private static final Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList("i", "me", "my", "a", "an", "the", "for", "to", "of", "in", "on", "with", "and", "or",
            "want", "need", "looking", "look", "find", "show", "get", "buy", "suggest", "recommend", "please", "good", "best", "some", "any",
            "under", "below", "less", "than", "max", "upto", "up", "within", "around", "about", "between", "dollars", "dollar", "usd", "rs", "inr", "rupee", "rupees",
            "budget", "price", "cost", "that", "is", "are", "can", "you", "would", "like", "something", "one", "new", "cheap", "affordable",
            "have", "has", "do", "does", "it", "this", "which", "what", "from", "by", "under", "over", "above", "at", "be", "so", "very"));

    private static final Pattern NUMBER = Pattern.compile("(?:[$₹€]\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(k\\b|K\\b)?");
    private static final Pattern BETWEEN = Pattern.compile("between\\s+(?:[$₹€]\\s*)?(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?\\s*(?:and|to|-)\\s*(?:[$₹€]\\s*)?(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDER = Pattern.compile("(?:under|below|less than|upto|up to|within|max(?:imum)?|not more than|<=?)\\s*(?:[$₹€]\\s*)?(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVER = Pattern.compile("(?:over|above|more than|at least|min(?:imum)?|>=?)\\s*(?:[$₹€]\\s*)?(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern AROUND = Pattern.compile("(?:around|about|approximately|roughly|~)\\s*(?:[$₹€]\\s*)?(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_MONEY = Pattern.compile("[$₹€]\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?|(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k)?\\s*(?:dollars?|usd|bucks|rupees?|inr)", Pattern.CASE_INSENSITIVE);

    private final JdbcClient jdbc;
    private final LlmGateway llm;

    public IntentExtractor(JdbcClient jdbc, LlmGateway llm) {
        this.jdbc = jdbc;
        this.llm = llm;
    }

    public Requirement extract(String text) {
        String q = text == null ? "" : text.trim();
        List<Map<String, Object>> categories = jdbc.sql("SELECT name, slug FROM categories ORDER BY id").query().listOfRows();
        List<String> brands = jdbc.sql("SELECT name FROM brands ORDER BY name").query(String.class).list();

        if (llm.available()) {
            String system = "Extract a shopping requirement from the customer message. Return JSON with fields: "
                    + "category (one of: " + categoryNames(categories) + " or null), budgetMin (number or null), budgetMax (number or null), "
                    + "brand (string or null), keywords (up to 6 short product feature words such as gaming, 16gb, oled, wireless), usage (short phrase or null). "
                    + "All prices and budgets are in INR (Indian rupees). Never invent a budget the customer did not state.";
            var llmResult = llm.completeJson(system, q, LlmRequirement.class);
            if (llmResult.isPresent()) {
                Requirement r = validate(llmResult.get(), categories, brands);
                if (r != null) return r;
            }
        }
        return rules(q, categories, brands);
    }

    // ----------------------------------------------------------------- LLM result validation

    Requirement validate(LlmRequirement l, List<Map<String, Object>> categories, List<String> brands) {
        BigDecimal min = money(l.budgetMin());
        BigDecimal max = money(l.budgetMax());
        if (min != null && max != null && min.compareTo(max) > 0) { BigDecimal t = min; min = max; max = t; }
        String[] cat = matchCategory(l.category(), categories);
        String brand = matchBrand(l.brand(), brands);
        List<String> kws = new ArrayList<>();
        if (l.keywords() != null) {
            for (String k : l.keywords()) {
                String c = k == null ? "" : k.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
                if (!c.isEmpty() && c.length() <= 30 && kws.size() < 6 && !kws.contains(c)) kws.add(c);
            }
        }
        String usage = l.usage() == null || l.usage().isBlank() || l.usage().length() > 60 ? null : l.usage().trim();
        return new Requirement(cat[0], cat[1], min, max, brand, kws, usage, false);
    }

    private static BigDecimal money(Double d) {
        if (d == null || d.isNaN() || d.isInfinite() || d < 0 || d > 10_000_000) return null;
        return BigDecimal.valueOf(d).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String categoryNames(List<Map<String, Object>> categories) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> c : categories) names.add((String) c.get("name"));
        return String.join(", ", names);
    }

    private static String[] matchCategory(String raw, List<Map<String, Object>> categories) {
        if (raw == null || raw.isBlank()) return new String[]{null, null};
        String r = raw.toLowerCase(Locale.ROOT).trim();
        for (Map<String, Object> c : categories) {
            String name = ((String) c.get("name")).toLowerCase(Locale.ROOT);
            String slug = (String) c.get("slug");
            if (r.equals(name) || r.equals(slug) || name.startsWith(r) || r.startsWith(name)) return new String[]{(String) c.get("name"), slug};
        }
        return new String[]{null, null};
    }

    private static String matchBrand(String raw, List<String> brands) {
        if (raw == null || raw.isBlank()) return null;
        for (String b : brands) if (b.equalsIgnoreCase(raw.trim())) return b;
        return null;
    }

    // ------------------------------------------------------------------ rule-based fallback

    Requirement rules(String q, List<Map<String, Object>> categories, List<String> brands) {
        String lower = q.toLowerCase(Locale.ROOT);
        BigDecimal[] budget = budget(lower);

        String catName = null;
        String catSlug = null;
        Set<String> consumed = new LinkedHashSet<>();
        for (Map<String, Object> c : categories) {
            String slug = (String) c.get("slug");
            String name = ((String) c.get("name")).toLowerCase(Locale.ROOT);
            List<String> words = new ArrayList<>(SYNONYMS.getOrDefault(slug, List.of()));
            words.add(name);
            words.add(name.endsWith("s") ? name.substring(0, name.length() - 1) : name);
            for (String w : words) {
                if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(w)).matcher(lower).find()) {
                    catName = (String) c.get("name");
                    catSlug = slug;
                    consumed.add(w);
                    break;
                }
            }
            if (catName != null) break;
        }
        String brand = null;
        for (String b : brands) {
            if (lower.contains(b.toLowerCase(Locale.ROOT))) { brand = b; consumed.add(b.toLowerCase(Locale.ROOT)); break; }
        }

        List<String> keywords = new ArrayList<>();
        String stripped = lower.replaceAll("[$₹€]\\s*\\d[\\d,.]*\\s*k?", " ");
        for (String t : stripped.split("[^a-z0-9+]+")) {
            if (t.length() < 3 || STOP.contains(t) || t.matches("\\d+(\\.\\d+)?")) continue;
            boolean isCat = false;
            for (String c : consumed) if (c.contains(t) || t.contains(c)) { isCat = true; break; }
            if (isCat) continue;
            if (!keywords.contains(t) && keywords.size() < 6) keywords.add(t);
        }
        return new Requirement(catName, catSlug, budget[0], budget[1], brand, keywords, null, false);
    }

    /** Returns {min, max}; either can be null. */
    static BigDecimal[] budget(String lower) {
        Matcher m = BETWEEN.matcher(lower);
        if (m.find()) {
            BigDecimal a = num(m.group(1), m.group(2));
            BigDecimal b = num(m.group(3), m.group(4));
            if (a != null && b != null) return a.compareTo(b) <= 0 ? new BigDecimal[]{a, b} : new BigDecimal[]{b, a};
        }
        m = UNDER.matcher(lower);
        if (m.find()) return new BigDecimal[]{null, num(m.group(1), m.group(2))};
        m = OVER.matcher(lower);
        if (m.find()) return new BigDecimal[]{num(m.group(1), m.group(2)), null};
        m = AROUND.matcher(lower);
        if (m.find()) {
            BigDecimal c = num(m.group(1), m.group(2));
            if (c != null) return new BigDecimal[]{c.multiply(new BigDecimal("0.8")).setScale(2, java.math.RoundingMode.HALF_UP),
                    c.multiply(new BigDecimal("1.2")).setScale(2, java.math.RoundingMode.HALF_UP)};
        }
        m = PLAIN_MONEY.matcher(lower);
        if (m.find()) {
            BigDecimal v = m.group(1) != null ? num(m.group(1), m.group(2)) : num(m.group(3), m.group(4));
            return new BigDecimal[]{null, v};
        }
        return new BigDecimal[]{null, null};
    }

    private static BigDecimal num(String digits, String k) {
        if (digits == null) return null;
        try {
            BigDecimal v = new BigDecimal(digits.replace(",", ""));
            if (k != null && !k.isEmpty()) v = v.multiply(BigDecimal.valueOf(1000));
            return v.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
