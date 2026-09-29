package com.eventtick.payment.config;

import com.eventtick.payment.dto.ErrorResponse;
import com.eventtick.payment.security.JwtAuthenticationFilter;
import com.eventtick.payment.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security wiring for the Payment Service. Stateless JWT
 * authentication, same shape as user-service's own {@code SecurityConfig}
 * — no sessions, no form login, no CSRF.
 *
 * <p><b>No public path at all</b> — unlike user-service (which has
 * {@code /api/auth/**}), every payment endpoint requires a caller identity;
 * there is nothing analogous to register/login here. The Phase 14 Step 1
 * actuator allowlist is the one exception, identical in shape to every
 * other service's.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                        // Phase 14 Step 1 parity: narrow allowlist, not
                        // /actuator/** — only health/liveness/readiness/
                        // prometheus are exposed at all (see
                        // management.endpoints.web.exposure in
                        // application.yml), and only these exact paths skip
                        // the JWT requirement below.
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/actuator/prometheus")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "You do not have permission to perform this action.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Missing, malformed, expired, or otherwise invalid tokens end up here
     * (see {@link JwtAuthenticationFilter} — it never rejects a request
     * itself, it just leaves the SecurityContext empty on failure). Same
     * {@link ErrorResponse} shape every other service in this project uses.
     */
    private void handleUnauthenticated(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required and either missing or invalid.");
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
                             String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(status.value(), errorCode, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
