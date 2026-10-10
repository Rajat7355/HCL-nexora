package com.shopsmart.inventory;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Staff-only stock view and restock. Orders never write here: the order_items trigger moves stock. */
@RestController
@RequestMapping("/api/admin")
public class InventoryAdminController {
    private final JdbcClient jdbc;

    public InventoryAdminController(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record RestockRequest(@NotBlank String sku, @NotNull Long warehouseId, @Min(1) int quantity) {}

    @GetMapping("/warehouses")
    public List<Map<String, Object>> warehouses() {
        return jdbc.sql("SELECT id, code, name, city, country, region FROM warehouses WHERE is_active ORDER BY name")
                .query().listOfRows();
    }

    @GetMapping("/inventory")
    public PageResponse<Map<String, Object>> inventory(@RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "false") boolean lowOnly,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        int pageNo = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            params.put("like", "%" + q.trim() + "%");
            where.append(" AND (p.name ILIKE :like OR p.sku ILIKE :like)");
        }
        if (lowOnly) where.append(" AND (i.quantity_on_hand - i.quantity_reserved) <= i.reorder_level");
        String from = """
                 FROM inventory i JOIN products p ON p.id = i.product_id JOIN warehouses w ON w.id = i.warehouse_id
                 LEFT JOIN v_sales_velocity v ON v.product_id = i.product_id AND v.warehouse_id = i.warehouse_id
                """;
        Long total = jdbc.sql("SELECT count(*) " + from + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("lim", pageSize);
        pageParams.put("off", pageNo * pageSize);
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT p.sku AS "productId", p.name, w.id AS "warehouseId", w.name AS warehouse,
                       i.quantity_on_hand AS "onHand", i.quantity_reserved AS reserved,
                       (i.quantity_on_hand - i.quantity_reserved) AS available, i.reorder_level AS "reorderLevel",
                       ((i.quantity_on_hand - i.quantity_reserved) <= i.reorder_level) AS low,
                       v.units_sold_30d AS "unitsSold30d", v.days_of_cover AS "daysOfCover"
                """ + from + where + " ORDER BY low DESC, available ASC, p.name LIMIT :lim OFFSET :off")
                .params(pageParams).query().listOfRows();
        return PageResponse.of(rows, pageNo, pageSize, total);
    }

    @GetMapping("/inventory/movements")
    public List<Map<String, Object>> movements(@RequestParam(required = false) String sku,
                                               @RequestParam(defaultValue = "50") int limit) {
        int lim = Math.min(Math.max(limit, 1), 200);
        JdbcClient.StatementSpec spec = jdbc.sql("""
                SELECT m.id, p.sku AS "productId", p.name, w.name AS warehouse, m.change_qty AS "changeQty",
                       m.reason, m.order_id AS "orderId", m.created_at AS "createdAt"
                FROM inventory_movements m JOIN products p ON p.id = m.product_id JOIN warehouses w ON w.id = m.warehouse_id
                """ + (sku == null || sku.isBlank() ? "" : " WHERE p.sku = :sku ") + " ORDER BY m.created_at DESC, m.id DESC LIMIT :lim");
        if (sku != null && !sku.isBlank()) spec = spec.param("sku", sku);
        return spec.param("lim", lim).query().listOfRows();
    }

    @PostMapping("/inventory/restock")
    @Transactional
    public Map<String, Object> restock(@Valid @RequestBody RestockRequest req) {
        Long productId = jdbc.sql("SELECT id FROM products WHERE sku = :sku").param("sku", req.sku())
                .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Product " + req.sku()));
        Integer wh = jdbc.sql("SELECT count(*) FROM warehouses WHERE id = :id").param("id", req.warehouseId())
                .query(Integer.class).single();
        if (wh == 0) throw ApiException.notFound("Warehouse");
        jdbc.sql("""
                INSERT INTO inventory (product_id, warehouse_id, quantity_on_hand)
                VALUES (:p, :w, :q)
                ON CONFLICT (product_id, warehouse_id)
                DO UPDATE SET quantity_on_hand = inventory.quantity_on_hand + EXCLUDED.quantity_on_hand, updated_at = now()
                """).param("p", productId).param("w", req.warehouseId()).param("q", req.quantity()).update();
        jdbc.sql("INSERT INTO inventory_movements (product_id, warehouse_id, change_qty, reason) VALUES (:p, :w, :q, 'RESTOCK')")
                .param("p", productId).param("w", req.warehouseId()).param("q", req.quantity()).update();
        return Map.of("productId", req.sku(), "added", req.quantity());
    }
}
