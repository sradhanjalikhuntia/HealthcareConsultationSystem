package com.apexcare.patient.security;

import com.apexcare.patient.entity.Role;
import com.apexcare.patient.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final int MIN_SECRET_BYTES = 32;   // HS256 requires >= 256 bits
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_LINKED_PATIENT_ID = "linkedPatientId";
    private static final String CLAIM_DOCTOR_CODE = "doctorCode";

    private final SecretKey key;
    private final Duration tokenTtl;

    public JwtService(
            @Value("${apexcare.jwt.secret:change-me-to-a-256-bit-or-longer-secret-key-for-hs256}") String secret,
            @Value("${apexcare.jwt.ttl:PT1H}") Duration tokenTtl) {
        this.key = buildKey(secret);
        this.tokenTtl = tokenTtl;
    }

    // -----------------------------------------------------------------
    // Parse
    // -----------------------------------------------------------------
    public JwtPrincipal parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw InvalidCredentialsException.missingToken();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtPrincipal(
                    uuidClaim(claims, CLAIM_USER_ID, true),
                    claims.get(CLAIM_USERNAME, String.class),
                    roleClaim(claims),
                    uuidClaim(claims, CLAIM_LINKED_PATIENT_ID, false),
                    claims.get(CLAIM_DOCTOR_CODE, String.class)
            );

        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            throw InvalidCredentialsException.expiredToken();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT invalid: {}", ex.getMessage());
            throw InvalidCredentialsException.invalidToken();
        }
    }

    // -----------------------------------------------------------------
    // Generate
    // -----------------------------------------------------------------
    public String generateToken(UUID userId,
                                String username,
                                Role role,
                                UUID linkedPatientId,
                                String doctorCode) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required to generate a token");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required to generate a token");
        }

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_LINKED_PATIENT_ID, linkedPatientId == null ? null : linkedPatientId.toString())
                .claim(CLAIM_DOCTOR_CODE, doctorCode)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Backwards-compatible overload (no doctorCode). */
    public String generateToken(UUID userId, String username, Role role, UUID linkedPatientId) {
        return generateToken(userId, username, role, linkedPatientId, null);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static UUID uuidClaim(Claims claims, String name, boolean required) {
        String raw = claims.get(name, String.class);
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new IllegalArgumentException("Missing required claim: " + name);
            }
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID in claim '" + name + "': " + raw, ex);
        }
    }

    private static Role roleClaim(Claims claims) {
        String raw = claims.get(CLAIM_ROLE, String.class);
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Missing required claim: " + CLAIM_ROLE);
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role in token: " + raw, ex);
        }
    }

    private static SecretKey buildKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("apexcare.jwt.secret must be configured");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "apexcare.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; got " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
