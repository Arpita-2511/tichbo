package com.eventtick.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request from its {@code Authorization: Bearer} JWT. A
 * missing or invalid token simply leaves the request unauthenticated — the
 * filter chain's rules then decide (401 for anything that requires
 * authentication).
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
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.parseToken(header.substring(BEARER_PREFIX.length())).ifPresent(caller -> {
                String authority = caller.admin() ? "ROLE_ADMIN" : "ROLE_CUSTOMER";
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        caller.userId().toString(), null, List.of(new SimpleGrantedAuthority(authority))));
            });
        }
        filterChain.doFilter(request, response);
    }
}
