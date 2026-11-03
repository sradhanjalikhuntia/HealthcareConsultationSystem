package com.apexcare.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ApexCare Auth Service")
                        .version("1.0.0")
                        .description("""
                                Authentication, JWT issuance, and user management.

                                Protected endpoints require a valid JWT in the
                                `Authorization: Bearer <token>` header. The
                                `/api/auth/login` and `/api/auth/register` endpoints
                                are public.
                                """)
                        .contact(new Contact()
                                .name("ApexCare Engineering")
                                .email("engineering@apexcare.com"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local"),
                        new Server().url("http://api-gateway:8085").description("Via API Gateway")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by this service. "
                                        + "Enter the token without the 'Bearer ' prefix.")));
        // No global .addSecurityItem() — annotate per-controller with
        // @SecurityRequirement(name = "bearerAuth") so public endpoints stay unlocked.
    }
}
