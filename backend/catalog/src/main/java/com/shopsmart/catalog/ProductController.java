package com.shopsmart.catalog;

import com.shopsmart.kit.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() { return products.categories(); }

    @GetMapping("/products")
    public PageResponse<Map<String, Object>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return products.search(q, category, brand, minPrice, maxPrice, minRating, inStock, sort, page, size);
    }

    @GetMapping("/products/filters")
    public Map<String, Object> filters(@RequestParam(required = false) String category) {
        return products.filters(category);
    }

    @GetMapping("/products/compare")
    public Map<String, Object> compare(@RequestParam List<String> skus) { return products.compare(skus); }

    @GetMapping("/products/{sku}")
    public Map<String, Object> detail(@PathVariable String sku) { return products.detail(sku); }
}
