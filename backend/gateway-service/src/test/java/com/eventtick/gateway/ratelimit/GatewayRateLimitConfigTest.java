package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11: the production {@code application.yml} really does bind the
 * documented static policy, and it is actually registered against the
 * policy id {@link RateLimitingGlobalFilter} uses — no
 * {@code @DynamicPropertySource} override, so this fails if someone weakens
 * or removes the shipped defaults. Mirrors {@code GatewayTimeoutConfigTest}
 * (Phase 7.5) for the same reason.
 */
@SpringBootTest
class GatewayRateLimitConfigTest {

    // Mirrors src/main/resources/application.yml's defaults.
    private static final int EXPECTED_REPLENISH_RATE = 5;
    private static final int EXPECTED_BURST_CAPACITY = 10;
    private static final int EXPECTED_REQUESTED_TOKENS = 1;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    void theStaticPolicy_isConfigured_toTheDocumentedConservativeDevValues() {
        RedisRateLimiter.Config policy = rateLimiter.getConfig().get(RateLimitingGlobalFilter.POLICY_ID);

        assertThat(policy).as("a policy must be registered for '%s'", RateLimitingGlobalFilter.POLICY_ID).isNotNull();
        assertThat(policy.getReplenishRate()).isEqualTo(EXPECTED_REPLENISH_RATE);
        assertThat(policy.getBurstCapacity()).isEqualTo(EXPECTED_BURST_CAPACITY);
        assertThat(policy.getRequestedTokens()).isEqualTo(EXPECTED_REQUESTED_TOKENS);
    }

    @Test
    void thePolicy_isTheSameForEveryRoute_notPerRouteOrPerPlan() {
        // Exactly one entry: RateLimitingGlobalFilter always charges against
        // the single fixed POLICY_ID, regardless of which route matched.
        assertThat(rateLimiter.getConfig()).hasSize(1).containsKey(RateLimitingGlobalFilter.POLICY_ID);
    }
}
