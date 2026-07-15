package com.example.insurancecrm.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessExpiryMillis;
    private final long refreshExpiryMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.access-expiry-minutes}") int accessExpiryMinutes,
                   @Value("${app.jwt.refresh-expiry-days}") int refreshExpiryDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMillis = (long) accessExpiryMinutes * 60 * 1000;
        this.refreshExpiryMillis = (long) refreshExpiryDays * 24 * 3600 * 1000;
    }

    public String generateAccessToken(String email, String userId, String role) {
        return buildToken(email, userId, role, TYPE_ACCESS, accessExpiryMillis);
    }

    public String generateRefreshToken(String email, String userId, String role) {
        return buildToken(email, userId, role, TYPE_REFRESH, refreshExpiryMillis);
    }

    private String buildToken(String email, String userId, String role, String type, long expiryMillis) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMillis))
                .signWith(signingKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractUserId(String token) {
        return extractClaims(token).get("userId", String.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** True only for a structurally valid, non-expired access token. */
    public boolean isAccessToken(String token) {
        return isTokenOfType(token, TYPE_ACCESS);
    }

    /** True only for a structurally valid, non-expired refresh token. */
    public boolean isRefreshToken(String token) {
        return isTokenOfType(token, TYPE_REFRESH);
    }

    /** True if the token was issued before the given cutoff (e.g. an admin force-logout) — a null cutoff means no forced logout has happened. */
    public boolean isIssuedBefore(String token, LocalDateTime cutoff) {
        if (cutoff == null) {
            return false;
        }
        LocalDateTime issuedAt = LocalDateTime.ofInstant(extractClaims(token).getIssuedAt().toInstant(), ZoneId.systemDefault());
        return issuedAt.isBefore(cutoff);
    }

    private boolean isTokenOfType(String token, String type) {
        try {
            return type.equals(extractClaims(token).get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
