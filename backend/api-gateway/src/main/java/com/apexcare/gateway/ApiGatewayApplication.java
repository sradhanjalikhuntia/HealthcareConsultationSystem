package com.apexcare.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.util.ClassUtils;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayApplication.class);

    public static void main(String[] args) {
        verifyReactiveStack();
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Spring Cloud Gateway runs on WebFlux (reactive). If the servlet stack
     * (spring-boot-starter-web) sneaks onto the classpath, the gateway fails at
     * runtime with a confusing error. Detect it here and fail loudly at startup.
     */
    private static void verifyReactiveStack() {
        if (ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet", null)) {
            throw new IllegalStateException("""
                    **********************************************************
                    spring-boot-starter-web (Servlet stack) detected.
                    Spring Cloud Gateway requires WebFlux.
                    Remove spring-boot-starter-web from your pom.xml / build.gradle.
                    **********************************************************
                    """);
        }
        log.info("Reactive stack verified — Spring Cloud Gateway starting up.");
    }
}
