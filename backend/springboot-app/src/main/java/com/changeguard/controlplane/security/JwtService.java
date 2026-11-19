package com.changeguard.controlplane.security;

import com.changeguard.controlplane.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and parses HS256 JWTs. Fails fast when the secret is missing or too short.
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(
            @Value("${changeguard.security.jwt.secret}") String secret,
            @Value("${changeguard.security.jwt.ttl-minutes}") long ttlMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "changeguard.security.jwt.secret (JWT_SECRET) must be set to at least 32 characters");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "changeguard.security.jwt.secret (JWT_SECRET) must be at least 32 characters for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(key)
                .compact();
    }

    /**
     * Parses a raw token (without the "Bearer " prefix).
     *
     * @return the embedded principal claims
     * @throws io.jsonwebtoken.JwtException when the token is invalid or expired
     */
    public AuthPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(
                java.util.UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                com.changeguard.controlplane.user.UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }
}
