package com.eventtick.booking.config;

import com.eventtick.booking.dto.ErrorResponse;
import com.eventtick.booking.security.JwtAuthenticationFilter;
import com.eventtick.booking.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication for booking-service, so booking ownership
 * (BR-07) can be enforced from the caller's validated identity.
 *
 * <p><b>{@code /internal/**} is permitted without a token.</b> It is the
 * service-to-service surface payment-service uses (read a booking, confirm,
 * cancel) — payment-service acts for the system, not for a particular user,
 * and its reconciliation sweep has no user token at all. The API Gateway has
 * no route for {@code /internal/**} (proven by a Gateway test), so a client
 * cannot reach it through the edge; like every other booking-service
 * endpoint before this change, it is only as private as this service's
 * network port.
 *
 * <p>{@code /api/bookings/{id}/confirm} does not exist as a public endpoint
 * and is denied for every caller: confirming a booking is payment-service's
 * job alone (via {@code /internal/**}), never a customer's or an admin's.
 *
 * <p>{@code /api/admin/**} additionally requires {@code ROLE_ADMIN} here, on
 * top of the Gateway's own rule (defense in depth, same as payment-service
 * validating the JWT the Gateway already validated).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        // The removed public confirm endpoint: denied for every
                        // role (an admin is not exempt — FR-39 is read-only) so
                        // callers get 403 (or 401 with no valid token) rather
                        // than whatever an unmapped path would produce.
                        .requestMatchers("/api/bookings/*/confirm").denyAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "You do not have permission to perform this action.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void handleUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException ex) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required and either missing or invalid.");
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String errorCode, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(status.value(), errorCode, message)));
    }
}
