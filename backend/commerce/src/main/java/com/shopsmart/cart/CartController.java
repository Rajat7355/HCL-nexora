package com.shopsmart.cart;

import com.shopsmart.kit.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) { this.cart = cart; }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal CurrentUser u) { return cart.view(u.id(), u.tier()); }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(@AuthenticationPrincipal CurrentUser u, @Valid @RequestBody CartService.AddItemRequest r) {
        cart.add(u.id(), r);
        return cart.view(u.id(), u.tier());
    }

    @PutMapping("/items/{sku}")
    public Map<String, Object> update(@AuthenticationPrincipal CurrentUser u, @PathVariable String sku,
                                      @Valid @RequestBody CartService.UpdateItemRequest r) {
        cart.setQuantity(u.id(), sku, r.quantity());
        return cart.view(u.id(), u.tier());
    }

    @DeleteMapping("/items/{sku}")
    public Map<String, Object> remove(@AuthenticationPrincipal CurrentUser u, @PathVariable String sku) {
        cart.remove(u.id(), sku);
        return cart.view(u.id(), u.tier());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal CurrentUser u) { cart.clear(u.id()); }
}
