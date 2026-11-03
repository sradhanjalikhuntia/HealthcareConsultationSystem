package com.apexcare.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    /**
     * This class is a Spring Cloud Gateway filter factory, not the application
     * entry point. Keep a no-op main method for tooling that validates an
     * individual source file as a standalone Java source.
     */
    public static void main(String[] args) {
        // The gateway application starts this component through Spring.
    }

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilterFactory.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;   // HS256 requires >= 256 bits
    private static final int MAX_TOKEN_LENGTH = 4096; // reject absurdly large tokens
    private static final String DEFAULT_SECRET =
            "change-me-to-a-256-bit-or-longer-secret-key-for-hs256";

    private static final String HDR_USER_ID     = "X-User-Id";
    private static final String HDR_USER_NAME   = "X-User-Name";
    private static final String HDR_USER_ROLE   = "X-User-Role";
    private static final String HDR_PATIENT_ID  = "X-Patient-Id";
    private static final String HDR_DOCTOR_CODE = "X-Doctor-Code";

    private static final String HDR_REQUEST_ID  = "X-Request-Id";

    private static final List<String> IDENTITY_HEADERS = List.of(
            HDR_USER_ID, HDR_USER_NAME, HDR_USER_ROLE, HDR_PATIENT_ID, HDR_DOCTOR_CODE
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecretKey secretKey;
    private final List<String> publicPaths;

    public JwtAuthGatewayFilterFactory(
            @Value("${apexcare.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
            @Value("${apexcare.jwt.public-paths:"
                    + "/api/auth/login,"
                    + "/api/auth/register,"
                    + "/api/auth/forgot-password,"
                    + "/api/auth/captcha,"
                    + "/actuator/**,"
                    + "/v3/api-docs/**,"
                    + "/swagger-ui/**,"
                    + "/swagger-ui.html"
                    + "}") List<String> publicPaths) {
        super(Config.class);
        this.secretKey = buildKey(secret);

        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("apexcare.jwt.secret is using the DEFAULT value. "
                    + "Override it in production via environment variable or config server.");
        }

        this.publicPaths = (publicPaths == null) ? List.of() : publicPaths;
        log.info("JwtAuthGatewayFilterFactory initialized with {} public path pattern(s): {}",
                this.publicPaths.size(), this.publicPaths);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String reqId = firstNonBlank(
                    (String) exchange.getAttributes().get(LoggingGlobalFilter.REQUEST_ID_ATTR),
                    request.getHeaders().getFirst(HDR_REQUEST_ID),
                    "-");

            // 1. CORS preflight never carries credentials — let it pass.
            if (HttpMethod.OPTIONS.equals(request.getMethod())) {
                return chain.filter(exchange);
            }

            // 2. Public endpoints (exact or Ant patterns).
            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            // 3. Extract bearer token.
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing/invalid Authorization header for {} reqId={}", path, reqId);
                return unauthorized(exchange, "Missing or invalid Authorization token", null);
            }
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                log.warn("Empty bearer token for {} reqId={}", path, reqId);
                return unauthorized(exchange, "Missing or invalid Authorization token", null);
            }
            if (token.length() > MAX_TOKEN_LENGTH) {
                log.warn("Oversized JWT ({} chars) for {} reqId={}", token.length(), path, reqId);
                return unauthorized(exchange, "Invalid JWT token", "invalid_token");
            }

            // 4. Parse & validate.
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId       = firstNonBlank(asString(claims, "userId"),   asString(claims, "sub"));
                String username     = firstNonBlank(asString(claims, "username"), asString(claims, "preferred_username"));
                String role         = asString(claims, "role");
                String linkedPatient = asString(claims, "linkedPatientId");
                String doctorCode   = asString(claims, "doctorCode");

                // Publish userId on the (shared) exchange attributes so the
                // LoggingGlobalFilter can include it in the access log.
                // The logging filter passes its mutated exchange *as* `exchange`
                // here, so this write is visible to its doFinally callback.
                if (StringUtils.hasText(userId)) {
                    exchange.getAttributes().put(LoggingGlobalFilter.USER_ID_ATTR, userId);
                }

                ServerHttpRequest mutatedRequest = request.mutate()
                        .headers(headers -> {
                            // Strip anything the client may have injected.
                            IDENTITY_HEADERS.forEach(headers::remove);

                            // Re-set the validated bearer token for downstream services.
                            headers.set(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);

                            putIfPresent(headers, HDR_USER_ID,     userId);
                            putIfPresent(headers, HDR_USER_NAME,   username);
                            putIfPresent(headers, HDR_USER_ROLE,   role);
                            putIfPresent(headers, HDR_PATIENT_ID,  linkedPatient);
                            putIfPresent(headers, HDR_DOCTOR_CODE, doctorCode);
                        })
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException ex) {
                log.warn("Expired JWT for {} reqId={}: {}", path, reqId, ex.getMessage());
                return unauthorized(exchange, "JWT token has expired", "invalid_token");
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("Invalid JWT for {} reqId={}: {}", path, reqId, ex.getMessage());
                return unauthorized(exchange, "Invalid JWT token", "invalid_token");
            } catch (Exception ex) {
                log.error("Unexpected error validating JWT for {} reqId={}", path, reqId, ex);
                return unauthorized(exchange, "Authentication failed", null);
            }
        };
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private boolean isPublicPath(String path) {
        for (String pattern : publicPaths) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static void putIfPresent(HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    /**
     * Safe claim extraction — coerces numbers/booleans to String
     * and returns null for missing claims, avoiding JJWT's
     * RequiredTypeException when the claim is not a String.
     */
    private static String asString(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return StringUtils.hasText(s) ? s : null;
        }
        return Objects.toString(value, null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        if (errorCode != null) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"" + errorCode + "\"");
        }

        String body = buildErrorBody(exchange, message);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(bytes.length);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String buildErrorBody(ServerWebExchange exchange, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("status", HttpStatus.UNAUTHORIZED.value());
        payload.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        payload.put("message", message);
        payload.put("path", exchange.getRequest().getURI().getPath());

        String reqId = (String) exchange.getAttributes().get(LoggingGlobalFilter.REQUEST_ID_ATTR);
        if (StringUtils.hasText(reqId)) {
            payload.put("requestId", reqId);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error body", e);
            return "{\"success\":false,\"status\":401,\"error\":\"Unauthorized\"}";
        }
    }

    private static SecretKey buildKey(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException("apexcare.jwt.secret must be configured");
        }
        byte[] bytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "apexcare.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; got " + bytes.length);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public static class Config {
        // no per-route options yet; add here if needed
    }
}
