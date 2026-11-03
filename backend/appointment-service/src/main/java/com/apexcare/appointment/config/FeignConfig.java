package com.apexcare.appointment.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default Feign configuration for appointment-service clients.
 *
 * <p>The authentication and correlation headers are forwarded by the
 * existing {@link FeignAuthConfig} interceptor.
 */
@Configuration(proxyBeanMethods = false)
public class FeignConfig {

    @Bean
    public RequestInterceptor requestContextForwarder() {
        return new FeignAuthConfig().authorizationForwarder();
    }
}
