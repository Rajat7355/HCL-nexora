package com.shopsmart.kit.security;

/** The authenticated principal, available in controllers via @AuthenticationPrincipal. */
public record CurrentUser(long id, String email, String tier, String region, boolean staff) {}
