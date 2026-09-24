package com.eventtick.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Turns a validated JWT into the gateway's authentication: the principal
 * name is the user id ({@code sub}), and the {@code role} claim becomes a
 * {@code ROLE_<role>} authority — the same authority
 * {@code user-service}'s filter grants. The full token (so the {@code
 * email} and {@code plan} claims too) stays reachable via
 * {@link JwtAuthenticationToken#getToken()}.
 *
 * <p>Nothing consumes the authority yet: in this phase the gateway only
 * authenticates, it has no role-based rules (see {@code GatewaySecurityConfig}).
 */
public class GatewayJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        // The decoder has already guaranteed "role" is a known role.
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")));
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
    }
}
