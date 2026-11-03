package com.apexcare.patient.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8082}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ApexCare Patient Service")
                        .version("1.0.0")
                        .description("""
                                REST API for managing patient records, vitals, beds, 
                                referrals, and audit logs in the ApexCare platform.
                                
                                All protected endpoints require a valid JWT in the 
                                `Authorization: Bearer <token>` header.
                                """)
                        .contact(new Contact()
                                .name("ApexCare Engineering")
                                .email("engineering@apexcare.com")
                                .url("https://apexcare.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://apexcare.com/license")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local"),
                        new Server().url("http://api-gateway:8085").description("Via API Gateway")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by ApexCare Auth Service. "
                                        + "Enter the token without the 'Bearer ' prefix.")));
        // No global .addSecurityItem() — annotate per-controller/per-endpoint
    }
}
