package com.shopsmart.catalog;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.PageResponse;
import com.shopsmart.kit.web.Rows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    static final String SELECT_CARD = """
            SELECT p.sku AS "productId", p.name, b.name AS brand, c.name AS category, c.slug AS "categorySlug",
                   p.price, p.currency, p.rating, p.rating_count AS "ratingCount", p.specs::text AS specs,
                   a.available_qty AS "availableQty", a.availability,
                   (SELECT i.url FROM product_images i WHERE i.product_id = p.id
                     ORDER BY i.is_primary DESC, i.sort_order LIMIT 1) AS "imageUrl"
            """;
    static final String FROM_CARD = """
            FROM products p
            JOIN brands b ON b.id = p.brand_id
            JOIN categories c ON c.id = p.category_id
            JOIN v_product_availability a ON a.product_id = p.id
            """;

    private final JdbcClient jdbc;

    public ProductService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public PageResponse<Map<String, Object>> search(String q, String category, List<String> brands, BigDecimal minPrice,
                                                    BigDecimal maxPrice, BigDecimal minRating, boolean inStock,
                                                    String sort, int page, int size) {
        int pageNo = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 60);

        StringBuilder where = new StringBuilder(" WHERE p.is_active ");
        Map<String, Object> params = new LinkedHashMap<>();
        String rank = "p.rating";
        boolean hasQuery = q != null && !q.isBlank();

        if (hasQuery) {
            params.put("q", q.trim());
            params.put("like", "%" + q.trim() + "%");
            where.append(" AND (p.search_vector @@ websearch_to_tsquery('english', :q)")
                 .append(" OR similarity(p.name, :q) > 0.25 OR p.name ILIKE :like OR b.name ILIKE :like)");
            rank = "(ts_rank(p.search_vector, websearch_to_tsquery('english', :q)) * 2 + similarity(p.name, :q))";
        }
        if (category != null && !category.isBlank()) {
            params.put("cat", category);
            where.append(" AND p.category_id IN (SELECT id FROM categories WHERE slug = :cat OR parent_id = (SELECT id FROM categories WHERE slug = :cat))");
        }
        if (brands != null && !brands.isEmpty()) {
            params.put("brands", brands);
            where.append(" AND b.slug IN (:brands)");
        }
        if (minPrice != null) { params.put("minPrice", minPrice); where.append(" AND p.price >= :minPrice"); }
        if (maxPrice != null) { params.put("maxPrice", maxPrice); where.append(" AND p.price <= :maxPrice"); }
        if (minRating != null) { params.put("minRating", minRating); where.append(" AND p.rating >= :minRating"); }
        if (inStock) where.append(" AND a.available_qty > 0");

        String order;
        String s = sort == null ? "" : sort;
        switch (s) {
            case "price_asc" -> order = "p.price ASC, p.id";
            case "price_desc" -> order = "p.price DESC, p.id";
            case "rating" -> order = "p.rating DESC, p.rating_count DESC, p.id";
            case "newest" -> order = "p.created_at DESC, p.id";
            default -> order = hasQuery ? rank + " DESC, p.id" : "p.rating DESC, p.rating_count DESC, p.id";
        }

        Long total = jdbc.sql("SELECT count(*) " + FROM_CARD + where).params(params).query(Long.class).single();

        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("lim", pageSize);
        pageParams.put("off", pageNo * pageSize);
        List<Map<String, Object>> rows = jdbc.sql(SELECT_CARD + FROM_CARD + where + " ORDER BY " + order + " LIMIT :lim OFFSET :off")
                .params(pageParams).query().listOfRows();
        return PageResponse.of(Rows.json(rows, "specs"), pageNo, pageSize, total);
    }

    public Map<String, Object> detail(String sku) {
        List<Map<String, Object>> rows = jdbc.sql(SELECT_CARD + ", p.description, p.created_at AS \"createdAt\" " + FROM_CARD
                        + " WHERE p.sku = :sku AND p.is_active")
                .param("sku", sku).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Product " + sku);
        Map<String, Object> product = Rows.json(rows.get(0), "specs");
        product.put("images", jdbc.sql("""
                SELECT i.url, i.alt_text AS "altText", i.is_primary AS "isPrimary"
                FROM product_images i JOIN products p ON p.id = i.product_id
                WHERE p.sku = :sku ORDER BY i.is_primary DESC, i.sort_order
                """).param("sku", sku).query().listOfRows());
        product.put("stockByWarehouse", jdbc.sql("""
                SELECT w.name AS warehouse, w.city, (i.quantity_on_hand - i.quantity_reserved) AS available
                FROM inventory i JOIN warehouses w ON w.id = i.warehouse_id JOIN products p ON p.id = i.product_id
                WHERE p.sku = :sku ORDER BY w.name
                """).param("sku", sku).query().listOfRows());
        product.put("related", jdbc.sql(SELECT_CARD + FROM_CARD + """
                 WHERE p.is_active AND p.sku <> :sku
                   AND p.category_id = (SELECT category_id FROM products WHERE sku = :sku)
                 ORDER BY p.rating DESC LIMIT 4
                """).param("sku", sku).query().listOfRows());
        return product;
    }

    /** Side-by-side comparison of 2 to 4 products with the union of their spec keys. */
    public Map<String, Object> compare(List<String> skus) {
        if (skus == null || skus.size() < 2 || skus.size() > 4) {
            throw ApiException.badRequest("Choose between 2 and 4 products to compare");
        }
        List<Map<String, Object>> rows = Rows.json(jdbc.sql(SELECT_CARD + FROM_CARD + " WHERE p.sku IN (:skus) AND p.is_active")
                .param("skus", skus).query().listOfRows(), "specs");
        if (rows.size() != skus.size()) throw ApiException.notFound("One or more products");
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) {
            Object specs = r.get("specs");
            if (specs instanceof com.fasterxml.jackson.databind.JsonNode node) {
                node.fieldNames().forEachRemaining(keys::add);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("products", rows);
        out.put("specKeys", new ArrayList<>(keys));
        return out;
    }

    /** Brands and price range for the filter sidebar. */
    public Map<String, Object> filters(String category) {
        Map<String, Object> out = new LinkedHashMap<>();
        String scope = (category == null || category.isBlank()) ? ""
                : " AND p.category_id IN (SELECT id FROM categories WHERE slug = :cat OR parent_id = (SELECT id FROM categories WHERE slug = :cat))";
        JdbcClient.StatementSpec brandSpec = jdbc.sql("""
                SELECT b.slug, b.name, count(*) AS count FROM products p JOIN brands b ON b.id = p.brand_id
                WHERE p.is_active""" + scope + " GROUP BY b.slug, b.name ORDER BY b.name");
        JdbcClient.StatementSpec priceSpec = jdbc.sql("SELECT min(p.price) AS min, max(p.price) AS max FROM products p WHERE p.is_active" + scope);
        if (!scope.isEmpty()) {
            brandSpec = brandSpec.param("cat", category);
            priceSpec = priceSpec.param("cat", category);
        }
        out.put("brands", brandSpec.query().listOfRows());
        out.put("price", priceSpec.query().singleRow());
        return out;
    }

    public List<Map<String, Object>> categories() {
        return jdbc.sql("""
                SELECT c.id, c.parent_id AS "parentId", c.name, c.slug, c.description,
                       (SELECT count(*) FROM products p WHERE p.is_active
                          AND (p.category_id = c.id OR p.category_id IN (SELECT id FROM categories k WHERE k.parent_id = c.id))) AS "productCount"
                FROM categories c ORDER BY c.parent_id NULLS FIRST, c.sort_order, c.name
                """).query().listOfRows();
    }
}
