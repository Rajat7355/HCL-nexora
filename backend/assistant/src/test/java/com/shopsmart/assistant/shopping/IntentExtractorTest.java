package com.shopsmart.assistant.shopping;

import com.shopsmart.kit.ai.LlmGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentExtractorTest {
    private static final List<Map<String, Object>> CATEGORIES = List.of(
            Map.of("name", "Laptops", "slug", "laptops"),
            Map.of("name", "Smartphones", "slug", "smartphones"),
            Map.of("name", "Headphones", "slug", "headphones"));
    private static final List<String> BRANDS = List.of("Apple", "Dell", "Sony");

    private final LlmGateway off = new LlmGateway() {
        public boolean available() { return false; }
        public String modelName() { return "none"; }
        public Optional<String> complete(String s, String u) { return Optional.empty(); }
        public <T> Optional<T> completeJson(String s, String u, Class<T> t) { return Optional.empty(); }
    };
    private final IntentExtractor extractor = new IntentExtractor(null, off);

    @Test
    void budgetPatterns() {
        assertEquals(new BigDecimal("1000.00"), IntentExtractor.budget("laptop under ₹1000")[1]);
        assertEquals(new BigDecimal("1500.00"), IntentExtractor.budget("phone below 1.5k")[1]);
        BigDecimal[] between = IntentExtractor.budget("between 500 and 800");
        assertEquals(new BigDecimal("500.00"), between[0]);
        assertEquals(new BigDecimal("800.00"), between[1]);
        assertEquals(new BigDecimal("5000.00"), IntentExtractor.budget("laptop under 5000 rupees")[1]);
        BigDecimal[] none = IntentExtractor.budget("a nice laptop");
        assertNull(none[0]);
        assertNull(none[1]);
    }

    @Test
    void gamingLaptopUnderBudget() {
        Requirement r = extractor.rules("I need a gaming laptop under ₹100000 with 16gb ram", CATEGORIES, BRANDS);
        assertEquals("laptops", r.categorySlug());
        assertEquals(new BigDecimal("100000.00"), r.budgetMax());
        assertTrue(r.keywords().contains("gaming"));
        assertTrue(r.keywords().contains("16gb"));
    }

    @Test
    void brandIsDetected() {
        Requirement r = extractor.rules("sony headphones with noise cancelling", CATEGORIES, BRANDS);
        assertEquals("Sony", r.brand());
        assertEquals("headphones", r.categorySlug());
    }

    @Test
    void vagueRequestHasNoCategory() {
        Requirement r = extractor.rules("hello can you help me", CATEGORIES, BRANDS);
        assertNull(r.categorySlug());
    }
}
