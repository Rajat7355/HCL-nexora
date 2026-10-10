package com.shopsmart.kit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${shopsmart.jwt.secret}") String secret,
                      @Value("${shopsmart.jwt.access-minutes:60}") long accessMinutes,
                      @Value("${shopsmart.jwt.refresh-days:7}") long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String createAccessToken(CurrentUser u) { return createAccessToken(u, UUID.randomUUID()); }

    public String createAccessToken(CurrentUser u, UUID sessionId) { return build(u, "access", accessTtl, sessionId); }

    public String createRefreshToken(CurrentUser u) { return createRefreshToken(u, UUID.randomUUID()); }

    public String createRefreshToken(CurrentUser u, UUID sessionId) { return build(u, "refresh", refreshTtl, sessionId); }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }

    public Duration refreshTtl() { return refreshTtl; }

    public UUID sessionId(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Wrong token type");
        }
        return UUID.fromString(claims.get("sid", String.class));
    }

    private String build(CurrentUser u, String type, Duration ttl, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(u.id()))
                .claim("email", u.email())
                .claim("tier", u.tier())
                .claim("region", u.region())
                .claim("staff", u.staff())
                .claim("typ", type)
                .claim("sid", sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Parses and validates a token of the expected type ("access" or "refresh"). Throws on any problem. */
    public CurrentUser parse(String token, String expectedType) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(c.get("typ", String.class))) {
            throw new IllegalArgumentException("Wrong token type");
        }
        Boolean staff = c.get("staff", Boolean.class);
        return new CurrentUser(Long.parseLong(c.getSubject()), c.get("email", String.class),
                c.get("tier", String.class), c.get("region", String.class), staff != null && staff);
    }
}
