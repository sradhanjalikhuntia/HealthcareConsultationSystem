package com.apexcare.gateway.filter;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    public  static final String REQUEST_ID_ATTR   = "gateway.requestId";
    public  static final String USER_ID_ATTR      = "gateway.userId";
    private static final String USER_ID_HEADER    = "X-User-Id";

    /** Only accept request ids matching this pattern; otherwise generate a fresh one. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    /** Path prefixes whose access logs are noise (probes, docs). */
    private static final Set<String> QUIET_PREFIXES = Set.of(
            "/actuator/health",
            "/actuator/ready",
            "/actuator/live",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final long slowRequestThresholdMs;

    public LoggingGlobalFilter(
            @Value("${apexcare.gateway.logging.slow-request-threshold-ms:1000}") long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        long start = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();

        String path   = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String client = resolveClientIp(request);
        String reqId  = resolveOrCreateRequestId(request);

        // Propagate the request id downstream so services can correlate logs.
        // Also echo it back on the response so clients can reference it.
        ServerWebExchange mutated = exchange.mutate()
                .request(request.mutate()
                        .headers(h -> h.set(REQUEST_ID_HEADER, reqId))
                        .build())
                .build();
        mutated.getResponse().getHeaders().set(REQUEST_ID_HEADER, reqId);

        // Attributes are shared by reference until mutate() is called.
        // Downstream filters (e.g. JWT) can write here and we'll see it in doFinally.
        mutated.getAttributes().put(REQUEST_ID_ATTR, reqId);

        boolean quiet = isQuiet(path);

        return chain.filter(mutated)
                .doFinally(signal -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000L;

                    HttpStatusCode statusCode = mutated.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 0;

                    // userId is set as an exchange attribute by the JWT filter.
                    String userId = resolveUserId(mutated);

                    if (quiet) {
                        if (log.isDebugEnabled()) {
                            log.debug("GATEWAY_ROUTED method={} path={} status={} durationMs={} client={} reqId={} signal={}",
                                    method, path, status, durationMs, client, reqId, signal);
                        }
                        return;
                    }

                    boolean abnormal = status == 0
                            || status >= 500
                            || signal != SignalType.ON_COMPLETE;

                    if (abnormal) {
                        log.warn("GATEWAY_ROUTED method={} path={} status={} durationMs={} client={} userId={} reqId={} signal={}",
                                method, path, status, durationMs, client, userId, reqId, signal);
                    } else {
                        log.info("GATEWAY_ROUTED method={} path={} status={} durationMs={} client={} userId={} reqId={}",
                                method, path, status, durationMs, client, userId, reqId);
                    }

                    if (durationMs >= slowRequestThresholdMs) {
                        log.warn("GATEWAY_SLOW method={} path={} status={} durationMs={} reqId={} thresholdMs={}",
                                method, path, status, durationMs, reqId, slowRequestThresholdMs);
                    }
                });
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static boolean isQuiet(String path) {
        for (String prefix : QUIET_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reuse a client-supplied request id only if it looks safe; otherwise
     * generate a fresh UUID. This prevents log injection and unbounded values.
     */
    private static String resolveOrCreateRequestId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (StringUtils.hasText(existing) && SAFE_REQUEST_ID.matcher(existing).matches()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Prefer the attribute set by the JWT filter on this exchange; fall back
     * to the (already sanitized) X-User-Id header, then to "-".
     */
    private static String resolveUserId(ServerWebExchange exchange) {
        Object attr = exchange.getAttributes().get(USER_ID_ATTR);
        if (attr != null && StringUtils.hasText(attr.toString())) {
            return attr.toString();
        }
        String header = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        return StringUtils.hasText(header) ? header : "-";
    }

    /**
     * NOTE: X-Forwarded-For is client-spoofable unless a trusted proxy
     * overwrites it. Adjust the strategy to match your ingress setup.
     */
    private static String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        if (request.getRemoteAddress() != null
                && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "-";
    }

    @Override
    public int getOrder() {
        // Outermost filter — wraps everything else so errors are still logged.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
