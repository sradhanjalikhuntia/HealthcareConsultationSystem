package com.apexcare.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;

    /** Paths that are noisy and should not be logged at INFO. */
    private static final Set<String> QUIET_PREFIXES = Set.of(
            "/actuator/health",
            "/actuator/ready",
            "/actuator/live",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        long startNanos = System.nanoTime();

        String method = request.getMethod();
        String path = request.getRequestURI();
        String requestId = resolveOrCreateRequestId(request);
        String clientIp = resolveClientIp(request);

        // Set request ID in MDC so every log line during this request carries it
        MDC.put(MDC_REQUEST_ID, requestId);
        // Echo back for client-side correlation
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();

            boolean quiet = isQuiet(path);

            if (quiet) {
                if (log.isDebugEnabled()) {
                    log.debug("HTTP method={} path={} status={} durationMs={} client={} reqId={}",
                            method, path, status, durationMs, clientIp, requestId);
                }
            } else if (status >= 500) {
                log.warn("HTTP method={} path={} status={} durationMs={} client={} reqId={}",
                        method, path, status, durationMs, clientIp, requestId);
            } else {
                log.info("HTTP method={} path={} status={} durationMs={} client={} reqId={}",
                        method, path, status, durationMs, clientIp, requestId);
            }

            if (!quiet && durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW method={} path={} status={} durationMs={} thresholdMs={} reqId={}",
                        method, path, status, durationMs,
                        SLOW_REQUEST_THRESHOLD_MS, requestId);
            }

            MDC.remove(MDC_REQUEST_ID);
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static boolean isQuiet(String path) {
        for (String prefix : QUIET_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String resolveOrCreateRequestId(HttpServletRequest request) {
        String existing = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(existing)) {
            // Cap length to prevent log injection / MDC bloat
            return existing.length() > 128 ? existing.substring(0, 128) : existing;
        }
        return UUID.randomUUID().toString();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
