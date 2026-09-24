package com.eventtick.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayJwtAuthenticationConverterTest {

    private static Jwt jwt(String role) {
        String subject = UUID.randomUUID().toString();
        return Jwt.withTokenValue("test-token-value")
                .header("alg", "HS256")
                .subject(subject)
                .claim("email", "test@example.com")
                .claim("role", role)
                .claim("plan", "PREMIUM")
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void roleBecomesAnAuthority_andSubjectBecomesThePrincipalName() {
        Jwt jwt = jwt("ADMIN");

        JwtAuthenticationToken auth =
                (JwtAuthenticationToken) new GatewayJwtAuthenticationConverter().convert(jwt).block();

        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo(jwt.getSubject());
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void planAndEmailStayAvailableFromTheToken() {
        JwtAuthenticationToken auth =
                (JwtAuthenticationToken) new GatewayJwtAuthenticationConverter().convert(jwt("CUSTOMER")).block();

        assertThat(auth).isNotNull();
        assertThat(auth.getToken().getClaimAsString("plan")).isEqualTo("PREMIUM");
        assertThat(auth.getToken().getClaimAsString("email")).isEqualTo("test@example.com");
    }
}
