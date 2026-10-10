package com.shopsmart.orders;

import com.shopsmart.kit.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    public record CheckoutRequest(Long addressId) {}

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(@AuthenticationPrincipal CurrentUser u, @RequestBody(required = false) CheckoutRequest r) {
        return orders.checkout(u.id(), r == null ? null : r.addressId());
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CurrentUser u) { return orders.list(u.id()); }

    @GetMapping("/{orderNumber}")
    public Map<String, Object> detail(@AuthenticationPrincipal CurrentUser u, @PathVariable String orderNumber) {
        return orders.detail(u.id(), orderNumber);
    }

    @GetMapping("/{orderNumber}/tracking")
    public Map<String, Object> tracking(@AuthenticationPrincipal CurrentUser u, @PathVariable String orderNumber) {
        return orders.tracking(u.id(), orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal CurrentUser u, @PathVariable String orderNumber) {
        return orders.cancel(u.id(), orderNumber);
    }
}
