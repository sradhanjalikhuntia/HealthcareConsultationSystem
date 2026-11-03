package com.apexcare.auth.security;

import com.apexcare.auth.exception.ErrorResponse;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .authorizeHttpRequests(auth -> auth
                        // ---- public ----
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/captcha",
                                "/api/auth/send-otp",
                                "/api/auth/verify-otp",
                                "/api/auth/check-username"
                        ).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()

                        // ---- doctor lookup — used by appointment-service, public and all users ----
                        .requestMatchers(HttpMethod.GET, "/api/auth/doctors", "/api/auth/doctors/**").permitAll()

                        // ---- user administration — ADMIN only ----
                        .requestMatchers("/api/auth/users", "/api/auth/users/**").hasRole("ADMIN")

                        // ---- everything else requires a valid token ----
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
    // Password encoder
    // -----------------------------------------------------------------
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // strength 12 — good balance
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
