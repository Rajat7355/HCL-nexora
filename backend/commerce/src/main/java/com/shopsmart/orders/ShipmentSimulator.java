package com.shopsmart.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock delivery engine (delivery events are out of scope in the brief, so they are simulated).
 * Every tick it moves each active order one step forward and writes a tracking event.
 * A share of orders hit a "delay" when they ship, so the demo has something to explain.
 */
@Component
@ConditionalOnProperty(name = "shopsmart.mock-shipping.enabled", havingValue = "true", matchIfMissing = true)
public class ShipmentSimulator {
    private static final Logger log = LoggerFactory.getLogger(ShipmentSimulator.class);

    private final JdbcClient jdbc;
    private final double stepSeconds;
    private final double delayProbability;

    public ShipmentSimulator(JdbcClient jdbc,
                             @Value("${shopsmart.mock-shipping.step-seconds:60}") double stepSeconds,
                             @Value("${shopsmart.mock-shipping.delay-probability:0.25}") double delayProbability) {
        this.jdbc = jdbc;
        this.stepSeconds = stepSeconds;
        this.delayProbability = delayProbability;
    }

    @Scheduled(fixedDelayString = "${shopsmart.mock-shipping.tick-ms:20000}", initialDelayString = "${shopsmart.mock-shipping.tick-ms:20000}")
    public void tick() {
        try {
            List<Map<String, Object>> due = jdbc.sql("""
                    SELECT id, status FROM orders
                    WHERE status IN ('PLACED','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY')
                      AND updated_at < now() - make_interval(secs => :s)
                    """).param("s", stepSeconds).query().listOfRows();
            for (Map<String, Object> o : due) {
                advance(((Number) o.get("id")).longValue(), (String) o.get("status"));
            }
        } catch (Exception e) {
            log.warn("Shipment simulator tick failed: {}", e.getMessage());
        }
    }

    static String next(String status) {
        return switch (status) {
            case "PLACED" -> "CONFIRMED";
            case "CONFIRMED" -> "PACKED";
            case "PACKED" -> "SHIPPED";
            case "SHIPPED" -> "OUT_FOR_DELIVERY";
            case "OUT_FOR_DELIVERY" -> "DELIVERED";
            default -> status;
        };
    }

    static String shipmentStatusFor(String orderStatus) {
        return switch (orderStatus) {
            case "SHIPPED" -> "IN_TRANSIT";
            case "OUT_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
            case "DELIVERED" -> "DELIVERED";
            default -> "LABEL_CREATED";
        };
    }

    static String describe(String orderStatus) {
        return switch (orderStatus) {
            case "CONFIRMED" -> "Order confirmed by the seller";
            case "PACKED" -> "Package packed and ready for pickup";
            case "SHIPPED" -> "Package picked up by the carrier";
            case "OUT_FOR_DELIVERY" -> "Out for delivery with the courier";
            case "DELIVERED" -> "Delivered to the customer";
            default -> "Status updated";
        };
    }

    static String locationFor(String orderStatus) {
        return switch (orderStatus) {
            case "CONFIRMED", "PACKED" -> "Fulfilment centre";
            case "SHIPPED" -> "Regional hub";
            case "OUT_FOR_DELIVERY" -> "Local delivery station";
            case "DELIVERED" -> "Customer address";
            default -> "In network";
        };
    }

    void advance(long orderId, String current) {
        String nextStatus = next(current);
        if (nextStatus.equals(current)) return;
        jdbc.sql("UPDATE orders SET status = :s, updated_at = now() WHERE id = :id").param("s", nextStatus).param("id", orderId).update();

        boolean delayed = nextStatus.equals("SHIPPED") && ThreadLocalRandom.current().nextDouble() < delayProbability;
        String shipStatus = delayed ? "DELAYED" : shipmentStatusFor(nextStatus);
        jdbc.sql("""
                UPDATE shipments SET status = :st,
                    shipped_at = CASE WHEN :next = 'SHIPPED' THEN now() ELSE shipped_at END,
                    delivered_at = CASE WHEN :next = 'DELIVERED' THEN now() ELSE delivered_at END,
                    estimated_delivery = CASE WHEN :delayed THEN COALESCE(estimated_delivery, current_date) + 2 ELSE estimated_delivery END,
                    delay_reason = CASE WHEN :delayed THEN 'Weather disruption at the regional hub'
                                        WHEN :next IN ('OUT_FOR_DELIVERY','DELIVERED') THEN NULL ELSE delay_reason END
                WHERE order_id = :o
                """).param("st", shipStatus).param("next", nextStatus).param("delayed", delayed).param("o", orderId).update();

        String description = delayed ? "Delayed: weather disruption at the regional hub" : describe(nextStatus);
        jdbc.sql("""
                INSERT INTO shipment_events (shipment_id, status, location, description)
                SELECT s.id, :st, :loc, :d FROM shipments s WHERE s.order_id = :o
                """).param("st", shipStatus).param("loc", locationFor(nextStatus)).param("d", description).param("o", orderId).update();
    }
}
