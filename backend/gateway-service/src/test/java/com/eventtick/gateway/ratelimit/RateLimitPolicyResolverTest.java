package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the combined (category, tier) resolution and the fallback
 * behavior — built with a real, small, in-memory policy set rather than a
 * Spring context, so it runs without booting the gateway. The
 * {@link DynamicPolicyService} is mocked to return values from the test's
 * own properties, simulating a populated dynamic cache.
 */
class RateLimitPolicyResolverTest {

    private RateLimitPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        RateLimitPolicyProperties properties = new RateLimitPolicyProperties();
        Map<RequestCategory, Map<UserTier, RateLimitPolicyProperties.PolicyValues>> policies = new EnumMap<>(RequestCategory.class);
        policies.put(RequestCategory.AUTH, Map.of(UserTier.PUBLIC, values(2, 2)));
        policies.put(RequestCategory.CATALOG, Map.of(
                UserTier.FREE, values(8, 8),
                UserTier.PRO, values(20, 20)));
        properties.setPolicies(policies);
        properties.setFallback(values(1, 1));

        DynamicPolicyService dynamicService = mock(DynamicPolicyService.class);
        when(dynamicService.lookupPolicy(any(), any())).thenAnswer(invocation -> {
            RequestCategory cat = invocation.getArgument(0);
            UserTier tier = invocation.getArgument(1);
            Map<UserTier, RateLimitPolicyProperties.PolicyValues> byTier = properties.getPolicies().get(cat);
            return byTier == null ? null : byTier.get(tier);
        });
        when(dynamicService.getCacheSnapshot()).thenReturn(Map.of());

        resolver = new RateLimitPolicyResolver(properties, dynamicService, new UserTierResolver());
    }

    private static RateLimitPolicyProperties.PolicyValues values(int replenishRate, int burstCapacity) {
        RateLimitPolicyProperties.PolicyValues values = new RateLimitPolicyProperties.PolicyValues();
        values.setReplenishRate(replenishRate);
        values.setBurstCapacity(burstCapacity);
        return values;
    }

    private static ServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private static Mono<RateLimitPolicy> resolveAs(RateLimitPolicyResolver resolver, String path, String role, String plan) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token-value")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", role)
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60));
        if (plan != null) {
            builder.claim("plan", plan);
        }
        JwtAuthenticationToken auth = new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return resolver.resolve(exchangeFor(path))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    @Test
    void catalogPlusFree_resolvesTheCatalogFreePolicy() {
        RateLimitPolicy policy = resolveAs(resolver, "/api/catalog/x", "CUSTOMER", "Free").block();

        assertThat(policy.id()).isEqualTo("CATALOG:FREE");
        assertThat(policy.burstCapacity()).isEqualTo(8);
    }

    @Test
    void catalogPlusPro_resolvesADifferentPolicy_fromFree() {
        RateLimitPolicy policy = resolveAs(resolver, "/api/catalog/x", "CUSTOMER", "Pro").block();

        assertThat(policy.id()).isEqualTo("CATALOG:PRO");
        assertThat(policy.burstCapacity()).isEqualTo(20);
    }

    @Test
    void unauthenticatedAuthRequest_resolvesThePublicPolicy() {
        RateLimitPolicy policy = resolver.resolve(exchangeFor("/api/auth/login")).block();

        assertThat(policy.id()).isEqualTo("AUTH:PUBLIC");
    }

    @Test
    void unknownPath_resolvesTheFallbackPolicy_withoutEverCheckingAuthentication() {
        RateLimitPolicy policy = resolver.resolve(exchangeFor("/api/does-not-exist")).block();

        assertThat(policy.id()).isEqualTo(RateLimitPolicyResolver.FALLBACK_POLICY_ID);
        assertThat(policy.burstCapacity()).isEqualTo(1);
    }

    @Test
    void aConfiguredCategoryWithAnUnconfiguredTier_fallsBackRatherThanErroring() {
        RateLimitPolicy policy = resolveAs(resolver, "/api/bookings", "CUSTOMER", "Free").block();

        assertThat(policy.id()).isEqualTo(RateLimitPolicyResolver.FALLBACK_POLICY_ID);
    }

    @Test
    void allConfiguredPolicies_includesTheFallback() {
        List<String> ids = resolver.allConfiguredPolicies().stream().map(RateLimitPolicy::id).toList();

        assertThat(ids).contains(RateLimitPolicyResolver.FALLBACK_POLICY_ID);
    }

    @Test
    void paymentCategory_resolvedCorrectly() {
        RateLimitPolicy policy = resolver.resolve(exchangeFor("/api/payments")).block();

        // No PAYMENT policy configured in this test's mock → fallback
        assertThat(policy.id()).isEqualTo(RateLimitPolicyResolver.FALLBACK_POLICY_ID);
    }

    @Test
    void adminCategory_resolvedCorrectly() {
        RateLimitPolicy policy = resolveAs(resolver, "/api/admin/something", "ADMIN", "Free").block();

        // No ADMIN policy configured in this test's mock → fallback
        assertThat(policy.id()).isEqualTo(RateLimitPolicyResolver.FALLBACK_POLICY_ID);
    }
}
