package com.shopsmart.assistant.shopping;

import java.math.BigDecimal;
import java.util.List;

/** What the shopper asked for, after interpretation and validation. */
public record Requirement(String category, String categorySlug, BigDecimal budgetMin, BigDecimal budgetMax,
                          String brand, List<String> keywords, String usage, boolean budgetFromPreferences) {

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (!keywords.isEmpty()) sb.append(String.join(" ", keywords)).append(' ');
        sb.append(category == null ? "products" : category.toLowerCase());
        if (brand != null) sb.append(" from ").append(brand);
        if (budgetMin != null && budgetMax != null) sb.append(" between ₹").append(budgetMin.stripTrailingZeros().toPlainString()).append(" and ₹").append(budgetMax.stripTrailingZeros().toPlainString());
        else if (budgetMax != null) sb.append(" under ₹").append(budgetMax.stripTrailingZeros().toPlainString());
        else if (budgetMin != null) sb.append(" above ₹").append(budgetMin.stripTrailingZeros().toPlainString());
        if (usage != null && !usage.isBlank()) sb.append(" for ").append(usage);
        return sb.toString().trim();
    }
}
