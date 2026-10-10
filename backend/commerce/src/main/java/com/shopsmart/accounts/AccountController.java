package com.shopsmart.accounts;

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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody AccountService.RegisterRequest r) { return accounts.register(r); }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody AccountService.LoginRequest r) { return accounts.login(r); }

    @PostMapping("/auth/refresh")
    public Map<String, Object> refresh(@Valid @RequestBody AccountService.RefreshRequest r) { return accounts.refresh(r); }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AccountService.LogoutRequest r) { accounts.logout(r); }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal CurrentUser u) { return accounts.profile(u.id()); }

    @PutMapping("/me")
    public Map<String, Object> updateMe(@AuthenticationPrincipal CurrentUser u, @Valid @RequestBody AccountService.ProfileRequest r) {
        return accounts.updateProfile(u.id(), r);
    }

    @PutMapping("/me/tier")
    public Map<String, Object> updateTier(@AuthenticationPrincipal CurrentUser u, @Valid @RequestBody AccountService.TierRequest r) {
        return accounts.updateTier(u.id(), r);
    }

    @GetMapping("/me/preferences")
    public Map<String, Object> preferences(@AuthenticationPrincipal CurrentUser u) { return accounts.preferences(u.id()); }

    @PutMapping("/me/preferences")
    public Map<String, Object> savePreferences(@AuthenticationPrincipal CurrentUser u, @RequestBody AccountService.PreferencesRequest r) {
        return accounts.savePreferences(u.id(), r);
    }

    @GetMapping("/me/addresses")
    public List<Map<String, Object>> addresses(@AuthenticationPrincipal CurrentUser u) { return accounts.addresses(u.id()); }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addAddress(@AuthenticationPrincipal CurrentUser u, @Valid @RequestBody AccountService.AddressRequest a) {
        return accounts.addAddress(u.id(), a);
    }

    @DeleteMapping("/me/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal CurrentUser u, @PathVariable long id) { accounts.deleteAddress(u.id(), id); }
}
