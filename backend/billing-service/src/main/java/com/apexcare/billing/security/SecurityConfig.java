package com.apexcare.billing.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/ward-discharge").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/billing/generate", "/api/billing/from-appointment").hasAnyRole("ADMIN", "BILLING_ADMIN", "RECEPTIONIST", "DOCTOR", "NURSE", "PATIENT", "SYSTEM")
                        .requestMatchers(HttpMethod.POST, "/api/billing/patient/*/auto-bill").hasAnyRole("ADMIN", "BILLING_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/billing/stats/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/billing/*/pay").hasAnyRole("ADMIN", "BILLING_ADMIN", "PATIENT", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/billing/*/receipt").hasAnyRole("ADMIN", "BILLING_ADMIN", "PATIENT", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/billing/patient/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "PATIENT", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/billing/appointment/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "PATIENT", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/billing/pharmacy/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "DOCTOR", "NURSE", "PHARMACIST")
                        .requestMatchers(HttpMethod.GET, "/api/billing/pharmacy/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "DOCTOR", "NURSE", "PHARMACIST", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/billing/lab/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "DOCTOR", "NURSE", "LAB_TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/billing/lab/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "DOCTOR", "NURSE", "LAB_TECHNICIAN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/billing").hasAnyRole("ADMIN", "BILLING_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/billing", "/api/billing/**").hasAnyRole("ADMIN", "BILLING_ADMIN", "RECEPTIONIST", "PATIENT")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
