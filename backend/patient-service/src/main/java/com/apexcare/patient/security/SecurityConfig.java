package com.apexcare.patient.security;

import com.apexcare.patient.exception.ErrorResponse;
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
                        // --- public: health, docs ---
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()

                        // --- public: auth ---
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/forgot-password",
                                "/api/auth/captcha"
                        ).permitAll()

                        // --- specific patient endpoints (must be before generic /api/patients/**) ---
                        .requestMatchers(HttpMethod.POST, "/api/patients/self-register").hasRole("PATIENT")
                        .requestMatchers(HttpMethod.POST, "/api/patients").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.DELETE, "/api/patients/**").hasRole("ADMIN")

                        // --- referrals ---
                        .requestMatchers(HttpMethod.GET, "/api/referrals/**").hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/referrals/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/referrals/**").hasAnyRole("ADMIN", "DOCTOR")

                        // --- vitals ---
                        .requestMatchers(HttpMethod.POST, "/api/vitals/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE")
                        .requestMatchers(HttpMethod.GET, "/api/vitals/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "PATIENT", "RECEPTIONIST")

                        // --- wards / beds ---
                        .requestMatchers("/api/wards", "/api/wards/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST", "BILLING_ADMIN")

                        // --- audit logs ---
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

                        // --- everything else needs a valid token ---
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // -----------------------------------------------------------------
    // 401 handler
    // -----------------------------------------------------------------
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                        "Unauthorized", "Authentication required or credentials invalid");
    }

    // -----------------------------------------------------------------
    // 403 handler
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
                MDC.get("requestId")
        );

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
