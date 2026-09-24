package com.eventtick.user.config;

import com.eventtick.user.dto.ErrorResponse;
import com.eventtick.user.security.JwtAuthenticationFilter;
import com.eventtick.user.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security wiring for the User Service. Stateless JWT
 * authentication — no sessions, no form login, no CSRF (there's no
 * browser session/cookie to forge in the first place).
 *
 * <p>Public: {@code /api/auth/**} (register, login). Everything else
 * requires a valid JWT, enforced by {@link JwtAuthenticationFilter}
 * running before Spring Security's own
 * {@link UsernamePasswordAuthenticationFilter}.
 *
 * <p>{@code @EnableMethodSecurity} is on so {@code @PreAuthorize} is
 * available for a future admin-only endpoint — none exists yet in this
 * phase beyond {@code /api/users/me}, which only requires being
 * authenticated (any role), so nothing currently exercises role-based
 * {@code @PreAuthorize} checks. The {@code ROLE_CUSTOMER}/{@code
 * ROLE_ADMIN} authority is already granted by
 * {@link JwtAuthenticationFilter}, so that's ready when a role-restricted
 * endpoint is added.
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // uses corsConfigurationSource() below
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
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
     * CORS for browser clients (the Vite dev server, by default). Without
     * this, a browser on a different origin than this service can't call
     * it: the preflight {@code OPTIONS} request carries no credentials, so
     * Spring Security would reject it before the real request is ever sent.
     *
     * <p>Deliberately narrow — only the origins listed in
     * {@code cors.allowed-origins} (never {@code *}), only the methods and
     * headers this API actually uses, and no credentials/cookies (auth is a
     * {@code Authorization: Bearer} header, not a cookie). Once an API
     * Gateway fronts these services, CORS belongs there instead.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Missing, malformed, expired, or otherwise invalid tokens on a
     * protected route all end up here (see {@link JwtAuthenticationFilter}
     * — it never rejects a request itself, it just leaves the
     * SecurityContext empty on failure). Returns the same
     * {@link ErrorResponse} shape as {@code GlobalExceptionHandler}, so
     * every error from this service looks consistent — Spring Security's
     * entry point runs before the DispatcherServlet, so
     * {@code @RestControllerAdvice} can't reach this case.
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
