package com.earist.ccs.scheduler.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    /**
     * Secret loaded from application.properties:
     *   jwt.secret=${JWT_SECRET:}
     *
     * In production, JWT_SECRET must be set to a Base64-encoded string
     * of at least 32 bytes (256 bits). If empty, we generate a random
     * key at startup (DEV ONLY — tokens invalidate on restart).
     */
    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("⚠️  JWT_SECRET not set. Generating a random key for THIS RUN.");
            log.warn("⚠️  Tokens will be invalidated on every restart. Set JWT_SECRET in production.");
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            return;
        }

        byte[] keyBytes;
        try {
            // Try Base64 first (recommended — `openssl rand -base64 48`)
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException e) {
            // Fall back to raw UTF-8 bytes
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must decode to at least 32 bytes (256 bits). " +
                "Current length: " + keyBytes.length + " bytes. " +
                "Generate one with: openssl rand -base64 48"
            );
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("✅ JWT key loaded ({} bytes, persistent across restarts)", keyBytes.length);
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}