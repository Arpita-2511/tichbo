package com.eventtick.booking.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Who is calling, taken from the JWT that {@link JwtAuthenticationFilter}
 * already validated — never from a request body or query parameter.
 */
public record CallerIdentity(UUID userId, boolean admin) {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public static CallerIdentity from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return new CallerIdentity(UUID.fromString(authentication.getName()), admin);
    }
}
