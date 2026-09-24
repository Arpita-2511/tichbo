package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 12: the production {@code application.yml} really does bind the
 * documented dynamic policy matrix, and every policy in it is actually
 * registered on the {@code RedisRateLimiter} bean — no
 * {@code @DynamicPropertySource} override, so this fails if someone weakens,
 * removes, or forgets to register part of the shipped matrix. Mirrors
 * {@code GatewayTimeoutConfigTest} (Phase 7.5) for the same reason.
 */
@SpringBootTest
class GatewayRateLimitConfigTest {

    @Autowired
    private RateLimitPolicyProperties properties;

    @Autowired
    private RateLimitPolicyResolver resolver;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    void auth_hasOnlyAPublicPolicy() {
        assertThat(properties.getPolicies().get(RequestCategory.AUTH).keySet())
                .containsExactly(UserTier.PUBLIC);
    }

    @Test
    void catalogBookingAndUser_eachHaveAllFourAuthenticatedTiers_andNoPublicEntry() {
        for (RequestCategory category : new RequestCategory[]{RequestCategory.CATALOG, RequestCategory.BOOKING, RequestCategory.USER}) {
            assertThat(properties.getPolicies().get(category).keySet())
                    .as("category %s", category)
                    .containsExactlyInAnyOrder(UserTier.FREE, UserTier.PRO, UserTier.PREMIUM, UserTier.ADMIN);
        }
    }

    @Test
    void higherTiers_areStrictlyMoreGenerous_withinEachAuthenticatedCategory() {
        for (RequestCategory category : new RequestCategory[]{RequestCategory.CATALOG, RequestCategory.BOOKING, RequestCategory.USER}) {
            var byTier = properties.getPolicies().get(category);
            int free = byTier.get(UserTier.FREE).getReplenishRate();
            int pro = byTier.get(UserTier.PRO).getReplenishRate();
            int premium = byTier.get(UserTier.PREMIUM).getReplenishRate();
            int admin = byTier.get(UserTier.ADMIN).getReplenishRate();

            assertThat(free).as("%s FREE < PRO", category).isLessThan(pro);
            assertThat(pro).as("%s PRO < PREMIUM", category).isLessThan(premium);
            assertThat(premium).as("%s PREMIUM < ADMIN", category).isLessThan(admin);
        }
    }

    @Test
    void theFallbackPolicy_isConfigured_andAsStrictAsPublicAuth() {
        assertThat(properties.getFallback().getReplenishRate())
                .isEqualTo(properties.getPolicies().get(RequestCategory.AUTH).get(UserTier.PUBLIC).getReplenishRate());
    }

    @Test
    void everyConfiguredPolicy_isActuallyRegisteredOnTheRedisRateLimiter() {
        for (RateLimitPolicy policy : resolver.allConfiguredPolicies()) {
            RedisRateLimiter.Config registered = rateLimiter.getConfig().get(policy.id());

            assertThat(registered).as("policy '%s' must be registered", policy.id()).isNotNull();
            assertThat(registered.getReplenishRate()).isEqualTo(policy.replenishRate());
            assertThat(registered.getBurstCapacity()).isEqualTo(policy.burstCapacity());
            assertThat(registered.getRequestedTokens()).isEqualTo(policy.requestedTokens());
        }
        // AUTH:PUBLIC, {CATALOG,BOOKING,USER}x{FREE,PRO,PREMIUM,ADMIN}, FALLBACK.
        assertThat(resolver.allConfiguredPolicies()).hasSize(1 + 3 * 4 + 1);
    }
}
