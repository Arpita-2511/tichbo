package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the tier half of dynamic policy resolution — see
 * {@code RateLimitPolicyResolverTest} for the combined (category, tier)
 * resolution and the client-cannot-spoof-a-header guarantees.
 */
class UserTierResolverTest {

    private final UserTierResolver resolver = new UserTierResolver();
    private final ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/catalog/content").build());

    private static Jwt jwt(String role, String plan) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token-value")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", role)
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60));
        if (plan != null) {
            builder.claim("plan", plan);
        }
        return builder.build();
    }

    private UserTier resolveWith(String role, String plan) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt(role, plan),
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        Context securityContext = ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth)));
        return resolver.resolve(exchange).contextWrite(securityContext).block();
    }

    @Test
    void noAuthenticationAtAll_isPublic() {
        assertThat(resolver.resolve(exchange).block()).isEqualTo(UserTier.PUBLIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Free", "FREE", "free", " Free "})
    void customerWithFreePlan_isFree_caseAndWhitespaceInsensitive(String planClaim) {
        assertThat(resolveWith("CUSTOMER", planClaim)).isEqualTo(UserTier.FREE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pro", "PRO", "pro"})
    void customerWithProPlan_isPro(String planClaim) {
        assertThat(resolveWith("CUSTOMER", planClaim)).isEqualTo(UserTier.PRO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Premium", "PREMIUM", "premium"})
    void customerWithPremiumPlan_isPremium(String planClaim) {
        assertThat(resolveWith("CUSTOMER", planClaim)).isEqualTo(UserTier.PREMIUM);
    }

    @Test
    void admin_isAdmin_regardlessOfPlan() {
        assertThat(resolveWith("ADMIN", "Free")).isEqualTo(UserTier.ADMIN);
        assertThat(resolveWith("ADMIN", "Premium")).isEqualTo(UserTier.ADMIN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Vip", "vip", "Gold", "not-a-real-plan"})
    void unrecognizedOrMissingPlan_defaultsToFree_neverToAHigherTier(String planClaim) {
        // "Vip" in particular: it exists in the frontend's own aspirational mock
        // data (types/index.ts), but not in the real plans table — it must not
        // be silently treated as anything above the safest default.
        assertThat(resolveWith("CUSTOMER", planClaim)).isEqualTo(UserTier.FREE);
    }
}
