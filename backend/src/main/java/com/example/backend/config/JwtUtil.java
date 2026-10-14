package com.example.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;

    // Default fallback value added after the colon (:) prevents startup crash
    public JwtUtil(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // Fallback method for global access token generation
    public String generateToken(String username, String role) {
        return generateToken(username, role, null);
    }

    // Multi-tenant token generation (includes factoryId)
    public String generateToken(String username, String role, Long factoryId) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("factoryId", factoryId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 Hours
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long extractFactoryId(String token) {
        return getClaims(token).get("factoryId", Long.class);
    }

    public boolean isTokenValid(String token) {
        try {
            return getClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}