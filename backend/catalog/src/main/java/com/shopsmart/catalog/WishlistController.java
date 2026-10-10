package com.shopsmart.catalog;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.security.CurrentUser;
import com.shopsmart.kit.web.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {
    private final JdbcClient jdbc;

    public WishlistController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser user) {
        return Rows.json(jdbc.sql(ProductService.SELECT_CARD + ", w.added_at AS \"addedAt\" " + ProductService.FROM_CARD
                        + " JOIN wishlist_items w ON w.product_id = p.id WHERE w.user_id = :uid ORDER BY w.added_at DESC")
                .param("uid", user.id()).query().listOfRows(), "specs");
    }

    @PostMapping("/{sku}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(@AuthenticationPrincipal CurrentUser user, @PathVariable String sku) {
        int n = jdbc.sql("""
                INSERT INTO wishlist_items (user_id, product_id)
                SELECT :uid, p.id FROM products p WHERE p.sku = :sku AND p.is_active
                ON CONFLICT (user_id, product_id) DO NOTHING
                """).param("uid", user.id()).param("sku", sku).update();
        Integer exists = jdbc.sql("SELECT count(*) FROM products WHERE sku = :sku").param("sku", sku).query(Integer.class).single();
        if (exists == 0) throw ApiException.notFound("Product " + sku);
        return Map.of("productId", sku, "added", n > 0);
    }

    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable String sku) {
        jdbc.sql("DELETE FROM wishlist_items WHERE user_id = :uid AND product_id = (SELECT id FROM products WHERE sku = :sku)")
                .param("uid", user.id()).param("sku", sku).update();
    }
}
