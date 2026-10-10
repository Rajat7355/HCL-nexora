package com.shopsmart.accounts;

import com.shopsmart.cart.CartService;
import com.shopsmart.orders.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Test/demo accounts, addresses, preferences and orders in several statuses.
 * Runs only when shopsmart.seed.demo-users=true and the users table is empty.
 * Passwords are hashed with the app's BCrypt encoder, so they cannot live in a SQL seed file.
 */
@Component
@ConditionalOnProperty(name = "shopsmart.seed.demo-users", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "Demo@1234";

    private final JdbcClient jdbc;
    private final AccountService accounts;
    private final CartService carts;
    private final OrderService orders;

    public DemoDataSeeder(JdbcClient jdbc, AccountService accounts, CartService carts, OrderService orders) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.carts = carts;
        this.orders = orders;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existing = jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
        if (existing > 0) return;
        List<String> skus = jdbc.sql("""
                SELECT p.sku FROM products p JOIN v_product_availability a ON a.product_id = p.id
                WHERE a.available_qty >= 20 ORDER BY p.id LIMIT 40
                """).query(String.class).list();
        if (skus.size() < 10) {
            log.warn("Demo seeding skipped: catalog seed data not loaded");
            return;
        }
        long admin = user("admin@shopsmart.test", "Admin@123", "Asha", "Admin", "PREMIUM", true);
        long basic = user("basic@shopsmart.test", DEMO_PASSWORD, "Bela", "Basic", "BASIC", false);
        long prime = user("prime@shopsmart.test", DEMO_PASSWORD, "Pooja", "Prime", "PRIME", false);
        long premium = user("premium@shopsmart.test", DEMO_PASSWORD, "Rahul", "Premium", "PREMIUM", false);
        for (long id : new long[]{admin, basic, prime, premium}) address(id);

        accounts.savePreferences(prime, new AccountService.PreferencesRequest(new BigDecimal("50000"), new BigDecimal("150000"),
                "INR", "EXPRESS", "Prefers lightweight laptops", List.of("laptops"), List.of()));

        // Basic: delivered, delayed-in-transit (frozen so the delay stays visible), fresh, cancelled
        String o1 = order(basic, skus.get(0), skus.get(1));
        stage(o1, "DELIVERED", "DELIVERED", null, 12, false);
        String o2 = order(basic, skus.get(2));
        stage(o2, "SHIPPED", "DELAYED", "Weather disruption at the regional hub", 4, true);
        order(basic, skus.get(3));
        String o4 = order(basic, skus.get(4));
        orders.cancel(basic, o4);
        // Prime and Premium
        String p1 = order(prime, skus.get(5));
        stage(p1, "OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY", null, 3, false);
        String p2 = order(prime, skus.get(6));
        stage(p2, "DELIVERED", "DELIVERED", null, 20, false);
        String m1 = order(premium, skus.get(7));
        stage(m1, "PACKED", "LABEL_CREATED", null, 1, false);

        // Sales history so the inventory agent has velocity to read
        log.info("Demo data ready. Staff login: admin@shopsmart.test / Admin@123. Shoppers: basic|prime|premium@shopsmart.test / {}", DEMO_PASSWORD);
    }

    private long user(String email, String password, String first, String last, String tier, boolean staff) {
        Map<String, Object> res = accounts.register(new AccountService.RegisterRequest(email, password, first, last, "GLOBAL"), tier, staff);
        @SuppressWarnings("unchecked")
        Map<String, Object> u = (Map<String, Object>) res.get("user");
        return ((Number) u.get("id")).longValue();
    }

    private void address(long userId) {
        accounts.addAddress(userId, new AccountService.AddressRequest("Demo Customer", "+91 98765 43210", "221B Market Street", null,
                "Jalandhar", "Punjab", "144011", "IN", true));
    }

    private String order(long userId, String... skus) {
        for (String sku : skus) carts.add(userId, new CartService.AddItemRequest(sku, 1));
        Map<String, Object> o = orders.checkout(userId, null);
        return (String) o.get("orderNumber");
    }

    /** Moves a freshly placed demo order into a given state. "freeze" parks it so the mock engine leaves it alone. */
    private void stage(String orderNumber, String orderStatus, String shipmentStatus, String delayReason, int daysAgo, boolean freeze) {
        jdbc.sql("""
                UPDATE orders SET status = :s, placed_at = now() - make_interval(days => :d),
                       updated_at = CASE WHEN :f THEN now() + interval '10 years' ELSE now() END
                WHERE order_number = :n
                """).param("s", orderStatus).param("d", daysAgo).param("f", freeze).param("n", orderNumber).update();
        jdbc.sql("""
                UPDATE shipments SET status = :ss, delay_reason = :dr,
                       shipped_at = CASE WHEN :ss IN ('IN_TRANSIT','DELAYED','OUT_FOR_DELIVERY','DELIVERED') THEN now() - make_interval(days => :d) ELSE NULL END,
                       delivered_at = CASE WHEN :ss = 'DELIVERED' THEN now() - make_interval(days => GREATEST(:d - 4, 0)) ELSE NULL END,
                       estimated_delivery = CASE WHEN :ss = 'DELAYED' THEN current_date + 2 ELSE estimated_delivery END
                WHERE order_id = (SELECT id FROM orders WHERE order_number = :n)
                """).param("ss", shipmentStatus).param("dr", delayReason).param("d", daysAgo).param("n", orderNumber).update();
        String description = switch (shipmentStatus) {
            case "DELIVERED" -> "Delivered to the customer";
            case "DELAYED" -> "Delayed: " + (delayReason == null ? "carrier delay" : delayReason.toLowerCase());
            case "OUT_FOR_DELIVERY" -> "Out for delivery with the courier";
            case "IN_TRANSIT" -> "In transit to the destination hub";
            default -> "Package packed and ready for pickup";
        };
        jdbc.sql("""
                INSERT INTO shipment_events (shipment_id, status, location, description)
                SELECT s.id, :ss, 'Regional hub', :d FROM shipments s JOIN orders o ON o.id = s.order_id WHERE o.order_number = :n
                """).param("ss", shipmentStatus).param("d", description).param("n", orderNumber).update();
    }
}
