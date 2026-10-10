package com.shopsmart.kit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads "Authorization: Bearer <jwt>" and sets the security context. Invalid tokens are simply ignored. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final JdbcClient jdbc;

    public JwtAuthFilter(JwtService jwt, JdbcClient jdbc) {
        this.jwt = jwt;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            CurrentUser user;
            java.util.UUID sessionId;
            try {
                user = jwt.parse(token, "access");
                sessionId = jwt.sessionId(token, "access");
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
                chain.doFilter(req, res);
                return;
            }
            CurrentUser activeUser = activeSessionUser(user, sessionId);
            if (activeUser != null) {
                List<GrantedAuthority> roles = new ArrayList<>();
                roles.add(new SimpleGrantedAuthority("ROLE_USER"));
                if (activeUser.staff()) roles.add(new SimpleGrantedAuthority("ROLE_STAFF"));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(activeUser, null, roles));
            } else {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private CurrentUser activeSessionUser(CurrentUser tokenUser, java.util.UUID sessionId) {
        return jdbc.sql("""
                SELECT u.id, u.email::text AS email, u.membership_tier AS tier, u.region, u.is_staff AS staff
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.id = :sessionId AND s.user_id = :userId
                  AND s.revoked_at IS NULL AND s.expires_at > now() AND u.is_active
                """).param("sessionId", sessionId).param("userId", tokenUser.id()).query().listOfRows().stream()
                .findFirst()
                .map(row -> new CurrentUser(((Number) row.get("id")).longValue(), (String) row.get("email"),
                        (String) row.get("tier"), (String) row.get("region"), Boolean.TRUE.equals(row.get("staff"))))
                .orElse(null);
    }
}
