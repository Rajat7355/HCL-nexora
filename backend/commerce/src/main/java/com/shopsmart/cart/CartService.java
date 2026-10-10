package com.shopsmart.cart;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.orders.Pricing;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    public record AddItemRequest(@NotBlank String sku, @Min(1) @Max(10) int quantity) {}

    public record UpdateItemRequest(@Min(1) @Max(10) int quantity) {}

    private final JdbcClient jdbc;

    public CartService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> view(long userId, String tier) {
        List<Map<String, Object>> items = jdbc.sql("""
                SELECT p.sku AS "productId", p.name, p.price, ci.quantity, (p.price * ci.quantity) AS "lineTotal",
                       a.available_qty AS "availableQty", a.availability,
                       (SELECT i.url FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.sort_order LIMIT 1) AS "imageUrl"
                FROM carts c JOIN cart_items ci ON ci.cart_id = c.id
                JOIN products p ON p.id = ci.product_id
                JOIN v_product_availability a ON a.product_id = p.id
                WHERE c.user_id = :u ORDER BY ci.added_at
                """).param("u", userId).query().listOfRows();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean problem = false;
        for (Map<String, Object> it : items) {
            subtotal = subtotal.add((BigDecimal) it.get("lineTotal"));
            int qty = ((Number) it.get("quantity")).intValue();
            int avail = ((Number) it.get("availableQty")).intValue();
            boolean ok = avail >= qty;
            it.put("inStock", ok);
            if (!ok) problem = true;
        }
        Pricing pricing = Pricing.compute(subtotal, tier);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("subtotal", pricing.subtotal());
        out.put("shipping", pricing.shipping());
        out.put("discount", pricing.discount());
        out.put("total", pricing.total());
        out.put("hasStockProblem", problem);
        return out;
    }

    @Transactional
    public void add(long userId, AddItemRequest r) {
        long productId = jdbc.sql("SELECT id FROM products WHERE sku = :s AND is_active").param("s", r.sku())
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Product " + r.sku()));
        long cartId = ensureCart(userId);
        int current = jdbc.sql("SELECT COALESCE(sum(quantity), 0) FROM cart_items WHERE cart_id = :c AND product_id = :p")
                .param("c", cartId).param("p", productId).query(Integer.class).single();
        int wanted = Math.min(current + r.quantity(), 10);
        checkStock(productId, wanted);
        jdbc.sql("""
                INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (:c, :p, :q)
                ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = :q
                """).param("c", cartId).param("p", productId).param("q", wanted).update();
        touch(cartId);
    }

    @Transactional
    public void setQuantity(long userId, String sku, int quantity) {
        long productId = jdbc.sql("SELECT id FROM products WHERE sku = :s").param("s", sku)
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Product " + sku));
        long cartId = ensureCart(userId);
        checkStock(productId, quantity);
        int n = jdbc.sql("UPDATE cart_items SET quantity = :q WHERE cart_id = :c AND product_id = :p")
                .param("q", quantity).param("c", cartId).param("p", productId).update();
        if (n == 0) throw ApiException.notFound("Cart item");
        touch(cartId);
    }

    public void remove(long userId, String sku) {
        jdbc.sql("""
                DELETE FROM cart_items WHERE product_id = (SELECT id FROM products WHERE sku = :s)
                  AND cart_id = (SELECT id FROM carts WHERE user_id = :u)
                """).param("s", sku).param("u", userId).update();
    }

    public void clear(long userId) {
        jdbc.sql("DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE user_id = :u)").param("u", userId).update();
    }

    private long ensureCart(long userId) {
        return jdbc.sql("""
                INSERT INTO carts (user_id) VALUES (:u)
                ON CONFLICT (user_id) DO UPDATE SET updated_at = now() RETURNING id
                """).param("u", userId).query(Long.class).single();
    }

    private void touch(long cartId) {
        jdbc.sql("UPDATE carts SET updated_at = now() WHERE id = :c").param("c", cartId).update();
    }

    /** Soft check against the availability view. The real guarantee is the stock trigger at checkout. */
    private void checkStock(long productId, int wanted) {
        int avail = jdbc.sql("SELECT available_qty FROM v_product_availability WHERE product_id = :p")
                .param("p", productId).query(Integer.class).optional().orElse(0);
        if (avail < wanted) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", avail <= 0 ? "This product is out of stock"
                    : "Only " + avail + " left in stock");
        }
    }
}
