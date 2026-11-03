package com.apexcare.appointment.config;

import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Global Feign interceptor that propagates authentication + correlation
 * headers from the inbound HTTP request to every outbound Feign call.
 *
 * <p>This means individual Feign method signatures do NOT need
 * {@code @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization}
 * parameters — the token flows automatically.
 *
 * <p><b>Async caveat:</b> {@link RequestContextHolder} only works on the
 * request thread. If a Feign call is made from an async / scheduled context,
 * the header will not be forwarded — pass it explicitly in that case.
 */
@Configuration(proxyBeanMethods = false)
public class FeignAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignAuthConfig.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    @Bean
    public RequestInterceptor authorizationForwarder() {
        return template -> {
            ServletRequestAttributes attrs = currentRequest();
            if (attrs == null) {
                log.debug("No request context — skipping auth header forwarding for Feign call");
                return;
            }

            String authorization = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            } else {
                log.debug("Inbound request had no Authorization header; Feign call will be unauthenticated");
            }

            // Propagate correlation ID for distributed tracing
            String requestId = attrs.getRequest().getHeader(REQUEST_ID_HEADER);
            if (!StringUtils.hasText(requestId)) {
                requestId = MDC.get(MDC_REQUEST_ID);
            }
            if (StringUtils.hasText(requestId)) {
                template.header(REQUEST_ID_HEADER, requestId);
            }
        };
    }

    private static ServletRequestAttributes currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes servlet) ? servlet : null;
    }
}
