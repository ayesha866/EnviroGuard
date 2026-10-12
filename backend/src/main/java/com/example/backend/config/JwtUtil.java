package com.example.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "EnviroGuardSuperSecretKeyThatIsVeryLongAndSecure1234567890!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    // Existing method (for backwards compatibility)
    public String generateToken(String username, String role) {
        return generateToken(username, role, null);
    }

    // Updated method supporting Multi-Tenancy (factoryId)
    public String generateToken(String username, String role, Long factoryId) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("factoryId", factoryId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 Hours
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

    public String extractClaim(String jwt, Object claimName) {
        return getClaims(jwt).get(claimName.toString(), String.class);
    }
}