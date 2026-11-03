package com.apexcare.billing.security;

import com.apexcare.billing.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${apexcare.jwt.secret:change-me-to-a-256-bit-or-longer-secret-key-for-hs256}") String secret) {
        this.key = Keys.hmacShaKeyFor(pad(secret).getBytes(StandardCharsets.UTF_8));
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String linked = claims.get("linkedPatientId", String.class);
            return new JwtPrincipal(
                    UUID.fromString(claims.get("userId", String.class)),
                    claims.get("username", String.class),
                    claims.get("role", String.class),
                    linked == null || linked.isBlank() ? null : UUID.fromString(linked),
                    claims.get("doctorCode", String.class)
            );
        } catch (ExpiredJwtException ex) {
            throw new AuthenticationException("Token has expired");
        } catch (Exception ex) {
            throw new AuthenticationException("Invalid token");
        }
    }

    public String generateToken(UUID userId, String username, String role, UUID linkedPatientId) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("linkedPatientId", linkedPatientId == null ? null : linkedPatientId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    private static String pad(String secret) {
        return secret.length() >= 32 ? secret : String.format("%-32s", secret).replace(' ', 'x');
    }
}
