package com.shopsmart.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tier rules in INR: BASIC pays shipping under threshold; PRIME ships free; PREMIUM also gets 5% off. */
public record Pricing(BigDecimal subtotal, BigDecimal shipping, BigDecimal discount, BigDecimal total) {
    public static final BigDecimal BASIC_SHIPPING = new BigDecimal("580.23");
    public static final BigDecimal FREE_SHIPPING_FROM = new BigDecimal("9686.72");

    public static Pricing compute(BigDecimal subtotalIn, String tier) {
        BigDecimal subtotal = subtotalIn.setScale(2, RoundingMode.HALF_UP);
        String t = tier == null ? "BASIC" : tier.toUpperCase();
        BigDecimal shipping = BigDecimal.ZERO.setScale(2);
        if (t.equals("BASIC") && subtotal.signum() > 0 && subtotal.compareTo(FREE_SHIPPING_FROM) < 0) {
            shipping = BASIC_SHIPPING;
        }
        BigDecimal discount = t.equals("PREMIUM")
                ? subtotal.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal total = subtotal.add(shipping).subtract(discount);
        return new Pricing(subtotal, shipping, discount, total);
    }
}
