package com.shopsmart.orders;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.Rows;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {
    private static final String[] CARRIERS = {"BlueDart", "Delhivery", "FedEx", "DHL Express"};
    private static final Set<String> CANCELLABLE = Set.of("PLACED", "CONFIRMED", "PACKED");

    private final JdbcClient jdbc;

    public OrderService(JdbcClient jdbc) { this.jdbc = jdbc; }

    /**
     * One transaction: order, items (the DB trigger takes stock and rejects overselling), shipment,
     * first tracking event, then the cart is emptied. Any failure rolls everything back.
     */
    @Transactional
    public Map<String, Object> checkout(long userId, Long addressId) {
        Map<String, Object> user = jdbc.sql("SELECT membership_tier AS tier FROM users WHERE id = :id").param("id", userId)
                .query().listOfRows().stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
        String tier = (String) user.get("tier");

        List<Map<String, Object>> items = jdbc.sql("""
                SELECT p.id AS "productId", p.name, p.price, ci.quantity
                FROM carts c JOIN cart_items ci ON ci.cart_id = c.id JOIN products p ON p.id = ci.product_id
                WHERE c.user_id = :u ORDER BY ci.id
                """).param("u", userId).query().listOfRows();
        if (items.isEmpty()) throw ApiException.badRequest("Your cart is empty");

        String addressSql = addressId == null
                ? "SELECT full_name, phone, line1, line2, city, state, postal_code, country FROM addresses WHERE user_id = :u AND is_default"
                : "SELECT full_name, phone, line1, line2, city, state, postal_code, country FROM addresses WHERE user_id = :u AND id = :a";
        JdbcClient.StatementSpec addrSpec = jdbc.sql(addressSql).param("u", userId);
        if (addressId != null) addrSpec = addrSpec.param("a", addressId);
        List<Map<String, Object>> addr = addrSpec.query().listOfRows();
        if (addr.isEmpty()) throw ApiException.badRequest("Please choose a delivery address");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            subtotal = subtotal.add(((BigDecimal) it.get("price")).multiply(BigDecimal.valueOf(((Number) it.get("quantity")).longValue())));
        }
        Pricing pricing = Pricing.compute(subtotal, tier);
        String orderNumber = "SS" + (10000000 + ThreadLocalRandom.current().nextInt(90000000));

        long orderId = jdbc.sql("""
                INSERT INTO orders (order_number, user_id, status, subtotal, shipping_fee, discount, total, shipping_address)
                VALUES (:n, :u, 'PLACED', :sub, :ship, :disc, :tot, CAST(:addr AS jsonb)) RETURNING id
                """)
                .param("n", orderNumber).param("u", userId).param("sub", pricing.subtotal()).param("ship", pricing.shipping())
                .param("disc", pricing.discount()).param("tot", pricing.total()).param("addr", Rows.toJson(addr.get(0)))
                .query(Long.class).single();

        for (Map<String, Object> it : items) {
            try {
                jdbc.sql("INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (:o, :p, :n, :price, :q)")
                        .param("o", orderId).param("p", it.get("productId")).param("n", it.get("name"))
                        .param("price", it.get("price")).param("q", it.get("quantity")).update();
            } catch (DataAccessException e) {
                String msg = e.getMostSpecificCause() == null ? "" : String.valueOf(e.getMostSpecificCause().getMessage());
                if (msg.contains("INSUFFICIENT_STOCK")) {
                    throw ApiException.conflict("INSUFFICIENT_STOCK", "Sorry, \"" + it.get("name") + "\" no longer has enough stock. Please update your cart.");
                }
                throw e;
            }
        }

        boolean express = Boolean.TRUE.equals(jdbc.sql("SELECT delivery_preference = 'EXPRESS' FROM customer_preferences WHERE user_id = :u")
                .param("u", userId).query(Boolean.class).optional().orElse(false));
        long shipmentId = jdbc.sql("""
                INSERT INTO shipments (order_id, carrier, tracking_number, status, estimated_delivery)
                VALUES (:o, :c, :t, 'LABEL_CREATED', :eta) RETURNING id
                """)
                .param("o", orderId).param("c", CARRIERS[ThreadLocalRandom.current().nextInt(CARRIERS.length)])
                .param("t", "TRK" + (1000000000L + ThreadLocalRandom.current().nextLong(8999999999L)))
                .param("eta", java.sql.Date.valueOf(LocalDate.now().plusDays(express ? 2 : 5)))
                .query(Long.class).single();
        jdbc.sql("INSERT INTO shipment_events (shipment_id, status, location, description) VALUES (:s, 'LABEL_CREATED', 'Fulfilment centre', 'Order received, shipping label created')")
                .param("s", shipmentId).update();

        jdbc.sql("DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE user_id = :u)").param("u", userId).update();
        return detail(userId, orderNumber);
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.sql("""
                SELECT o.order_number AS "orderNumber", o.status, o.total, o.currency, o.placed_at AS "placedAt",
                       (SELECT count(*) FROM order_items i WHERE i.order_id = o.id) AS "itemCount",
                       (SELECT string_agg(i.product_name, ', ' ORDER BY i.id) FROM order_items i WHERE i.order_id = o.id) AS "itemNames",
                       t.shipment_status AS "shipmentStatus", t.estimated_delivery AS "estimatedDelivery"
                FROM orders o LEFT JOIN v_order_tracking t ON t.order_id = o.id
                WHERE o.user_id = :u ORDER BY o.placed_at DESC
                """).param("u", userId).query().listOfRows();
    }

    public Map<String, Object> detail(long userId, String orderNumber) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT o.id, o.order_number AS "orderNumber", o.status, o.subtotal, o.shipping_fee AS "shippingFee",
                       o.discount, o.total, o.currency, o.shipping_address::text AS "shippingAddress",
                       o.placed_at AS "placedAt", o.cancelled_at AS "cancelledAt"
                FROM orders o WHERE o.order_number = :n AND o.user_id = :u
                """).param("n", orderNumber).param("u", userId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Order " + orderNumber);
        Map<String, Object> order = Rows.json(rows.get(0), "shippingAddress");
        long id = ((Number) order.remove("id")).longValue();
        order.put("items", jdbc.sql("""
                SELECT p.sku AS "productId", i.product_name AS name, i.unit_price AS "unitPrice", i.quantity, i.line_total AS "lineTotal"
                FROM order_items i JOIN products p ON p.id = i.product_id WHERE i.order_id = :o ORDER BY i.id
                """).param("o", id).query().listOfRows());
        order.put("tracking", tracking(userId, orderNumber));
        order.put("cancellable", CANCELLABLE.contains((String) order.get("status")));
        return order;
    }

    public Map<String, Object> tracking(long userId, String orderNumber) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT order_number AS "orderNumber", order_status AS "orderStatus", carrier, tracking_number AS "trackingNumber",
                       shipment_status AS "shipmentStatus", estimated_delivery AS "estimatedDelivery", delay_reason AS "delayReason",
                       last_event AS "lastEvent", last_location AS "lastLocation", last_event_at AS "lastEventAt", shipment_id AS "shipmentId"
                FROM v_order_tracking WHERE order_number = :n AND user_id = :u
                """).param("n", orderNumber).param("u", userId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Order " + orderNumber);
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        Object shipmentId = out.remove("shipmentId");
        List<Map<String, Object>> events = shipmentId == null ? List.of() : jdbc.sql("""
                SELECT status, location, description, occurred_at AS "occurredAt"
                FROM shipment_events WHERE shipment_id = :s ORDER BY occurred_at DESC, id DESC
                """).param("s", shipmentId).query().listOfRows();
        out.put("events", events);
        return out;
    }

    /** Allowed until the parcel ships. The orders trigger puts the stock back and writes RETURN ledger rows. */
    @Transactional
    public Map<String, Object> cancel(long userId, String orderNumber) {
        List<Map<String, Object>> rows = jdbc.sql("SELECT id, status FROM orders WHERE order_number = :n AND user_id = :u FOR UPDATE")
                .param("n", orderNumber).param("u", userId).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Order " + orderNumber);
        String status = (String) rows.get(0).get("status");
        long id = ((Number) rows.get(0).get("id")).longValue();
        if (!CANCELLABLE.contains(status)) {
            throw ApiException.conflict("CANNOT_CANCEL", "This order is already " + status.toLowerCase().replace('_', ' ')
                    + " and can no longer be cancelled. You can request a return after delivery.");
        }
        jdbc.sql("UPDATE orders SET status = 'CANCELLED', cancelled_at = now(), updated_at = now() WHERE id = :id").param("id", id).update();
        jdbc.sql("""
                INSERT INTO shipment_events (shipment_id, status, location, description)
                SELECT s.id, 'CANCELLED', NULL, 'Order cancelled by customer' FROM shipments s WHERE s.order_id = :id
                """).param("id", id).update();
        return detail(userId, orderNumber);
    }
}
