package com.example.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtUtil {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final SecureRandom FALLBACK_RANDOM = new SecureRandom();

    private final SecretKey secretKey;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret:}") String configuredSecret,
            @Value("${jwt.expiration:86400000}") long expiration,
            Environment environment) {
        this.expiration = expiration;
        this.secretKey = resolveSecretKey(configuredSecret, environment);
    }

    public String generateToken(String email, String role) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

    private SecretKey resolveSecretKey(String configuredSecret, Environment environment) {
        if (StringUtils.hasText(configuredSecret)) {
            byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MINIMUM_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes.");
            }
            return Keys.hmacShaKeyFor(bytes);
        }

        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("JWT_SECRET is required in production.");
        }

        byte[] generatedSecret = new byte[MINIMUM_SECRET_BYTES];
        FALLBACK_RANDOM.nextBytes(generatedSecret);
        log.warn("JWT_SECRET is not configured; using an ephemeral development key for this process.");
        return Keys.hmacShaKeyFor(generatedSecret);
    }
}
