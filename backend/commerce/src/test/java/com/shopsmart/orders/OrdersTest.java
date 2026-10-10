package com.shopsmart.orders;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersTest {

    @Test
    void basicPaysShippingUnderThreshold() {
        Pricing p = Pricing.compute(new BigDecimal("5000.00"), "BASIC");
        assertEquals(new BigDecimal("580.23"), p.shipping());
        assertEquals(new BigDecimal("5580.23"), p.total());
    }

    @Test
    void basicShipsFreeFromThreshold() {
        Pricing p = Pricing.compute(new BigDecimal("9686.72"), "BASIC");
        assertEquals(0, p.shipping().signum());
        assertEquals(new BigDecimal("9686.72"), p.total());
    }

    @Test
    void basicShippingThresholdUsesConvertedRupees() {
        assertEquals(new BigDecimal("580.23"), Pricing.compute(new BigDecimal("9686.71"), "BASIC").shipping());
        assertEquals(0, Pricing.compute(new BigDecimal("9686.72"), "BASIC").shipping().signum());
    }

    @Test
    void primeShipsFreeAndPremiumGetsFivePercent() {
        assertEquals(0, Pricing.compute(new BigDecimal("20"), "PRIME").shipping().signum());
        Pricing premium = Pricing.compute(new BigDecimal("200"), "PREMIUM");
        assertEquals(new BigDecimal("10.00"), premium.discount());
        assertEquals(new BigDecimal("190.00"), premium.total());
    }

    @Test
    void totalAlwaysMatchesDatabaseConstraint() {
        for (String tier : new String[]{"BASIC", "PRIME", "PREMIUM"}) {
            Pricing p = Pricing.compute(new BigDecimal("123.45"), tier);
            assertEquals(p.total(), p.subtotal().add(p.shipping()).subtract(p.discount()));
        }
    }

    @Test
    void shipmentFlowEndsAtDelivered() {
        String s = "PLACED";
        int guard = 0;
        while (!s.equals("DELIVERED") && guard++ < 10) s = ShipmentSimulator.next(s);
        assertEquals("DELIVERED", s);
        assertEquals("DELIVERED", ShipmentSimulator.next("DELIVERED"));
        assertEquals("IN_TRANSIT", ShipmentSimulator.shipmentStatusFor("SHIPPED"));
    }
}
