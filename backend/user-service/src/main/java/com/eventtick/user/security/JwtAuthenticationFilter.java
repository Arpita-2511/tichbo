package com.eventtick.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <token>}, validates it via
 * {@link JwtService}, and — if valid — populates the
 * {@code SecurityContext} so downstream code (e.g.
 * {@code UserController.me}) can read the authenticated user's identity.
 *
 * <p>Deliberately does not use a {@code UserDetailsService}/
 * {@code AuthenticationManager}: this is a simple self-issued-JWT setup,
 * not a Spring Security form-login/session flow, so that machinery isn't
 * needed. The {@code Authentication} principal is set to the user's id
 * (as a string) — {@code UserController} parses it back to a
 * {@code UUID}.
 *
 * <p>If the token is missing or fails validation, this filter simply
 * does nothing (no authentication is set) and lets the request continue;
 * {@code SecurityConfig}'s {@code authenticated()} requirement on
 * protected routes is what actually rejects it, via the custom
 * {@code AuthenticationEntryPoint} → 401. This filter never itself
 * writes an error response, so public routes (register/login) are
 * unaffected by an absent or garbage token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtService::parseToken)
                .ifPresent(this::setAuthentication);

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void setAuthentication(JwtAuthentication auth) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + auth.role().name()));
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(auth.userId().toString(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
