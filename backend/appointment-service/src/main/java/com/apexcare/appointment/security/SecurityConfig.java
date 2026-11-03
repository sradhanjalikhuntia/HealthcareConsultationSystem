package com.apexcare.appointment.security;

import com.apexcare.appointment.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String MDC_REQUEST_ID = "requestId";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .authorizeHttpRequests(auth -> auth
                        // ---- public ----
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // ---- emergency alerts (declared before generic appointments) ----
                        .requestMatchers("/api/appointments/emergency/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE")

                        // ---- live waiting queue ----
                        .requestMatchers("/api/appointments/queue/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")

                        // ---- clinical AI triage & drug checks ----
                        .requestMatchers("/api/appointments/ai/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE")

                        // ---- appointment endpoints ----
                        // Booking: patient self-book, staff-book
                        .requestMatchers(HttpMethod.POST, "/api/appointments", "/api/appointments/")
                                .hasAnyRole("ADMIN", "RECEPTIONIST", "PATIENT")

                        // List / view / stats / availability: staff (including billing admin) see all; patient sees only own (ownership in service)
                        .requestMatchers(HttpMethod.GET, "/api/appointments", "/api/appointments/", "/api/appointments/**")
                                .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE", "BILLING_ADMIN", "PATIENT")

                        // Update / cancel / reschedule: staff + patient
                        .requestMatchers(HttpMethod.PUT, "/api/appointments/**")
                                .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/**")
                                .hasAnyRole("ADMIN", "RECEPTIONIST", "DOCTOR", "PATIENT")

                        // Delete: admin only
                        .requestMatchers(HttpMethod.DELETE, "/api/appointments/**")
                                .hasRole("ADMIN")

                        // ---- everything else requires authentication ----
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // -----------------------------------------------------------------
    // 401 handler — JSON, not HTML
    // -----------------------------------------------------------------
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                        "Unauthorized", "Authentication required or credentials invalid");
    }

    // -----------------------------------------------------------------
    // 403 handler — JSON, not HTML
    // -----------------------------------------------------------------
    @Bean
    public AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, request, HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden", "Insufficient privileges");
    }

    // -----------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------
    private void writeError(HttpServletResponse response,
                            HttpServletRequest request,
                            int status,
                            String error,
                            String message) throws IOException {

        log.warn("{} {} {} — {}", status, request.getMethod(), request.getRequestURI(), message);

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status,
                error,
                message,
                request.getRequestURI(),
                MDC.get(MDC_REQUEST_ID)
        );

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
