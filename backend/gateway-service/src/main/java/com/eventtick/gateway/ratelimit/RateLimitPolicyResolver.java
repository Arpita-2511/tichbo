package com.eventtick.gateway.ratelimit;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 12 / Phase 19: picks which {@link RateLimitPolicy} applies to a
 * request. Phase 19 changed the policy source from static
 * {@link RateLimitPolicyProperties} to the {@link DynamicPolicyService}'s
 * in-memory cache, which is backed by Redis and updated at runtime by
 * admin CRUD operations. The fallback policy still comes from
 * {@code application.yml} and is always available.
 *
 * <p><b>Resolution precedence</b> is unchanged from Phase 12:
 * category (from path) + tier (from JWT) → dynamic cache lookup →
 * fallback if no matching policy exists or the policy is disabled.
 */
@Component
class RateLimitPolicyResolver {

    static final String FALLBACK_POLICY_ID = "FALLBACK";

    private final RateLimitPolicyProperties staticProperties;
    private final DynamicPolicyService dynamicPolicyService;
    private final UserTierResolver tierResolver;

    RateLimitPolicyResolver(RateLimitPolicyProperties staticProperties,
                             DynamicPolicyService dynamicPolicyService,
                             UserTierResolver tierResolver) {
        this.staticProperties = staticProperties;
        this.dynamicPolicyService = dynamicPolicyService;
        this.tierResolver = tierResolver;
    }

    Mono<RateLimitPolicy> resolve(ServerWebExchange exchange) {
        RequestCategory category = RequestCategoryClassifier.classify(exchange.getRequest().getPath().value());
        if (category == RequestCategory.UNKNOWN) {
            return Mono.just(fallback());
        }
        return tierResolver.resolve(exchange).map(tier -> policyFor(category, tier));
    }

    private RateLimitPolicy policyFor(RequestCategory category, UserTier tier) {
        RateLimitPolicyProperties.PolicyValues values = dynamicPolicyService.lookupPolicy(category, tier);
        if (values == null) {
            return fallback();
        }
        return toPolicy(category + ":" + tier, values);
    }

    private RateLimitPolicy fallback() {
        return toPolicy(FALLBACK_POLICY_ID, staticProperties.getFallback());
    }

    private static RateLimitPolicy toPolicy(String id, RateLimitPolicyProperties.PolicyValues values) {
        return new RateLimitPolicy(id, values.getReplenishRate(), values.getBurstCapacity(), values.getRequestedTokens());
    }

    /**
     * Every policy that must be registered on the {@code RedisRateLimiter}
     * at startup — everything in the dynamic cache, plus the fallback.
     */
    List<RateLimitPolicy> allConfiguredPolicies() {
        List<RateLimitPolicy> all = new ArrayList<>();
        for (Map.Entry<String, DynamicRateLimitPolicy> entry : dynamicPolicyService.getCacheSnapshot().entrySet()) {
            DynamicRateLimitPolicy p = entry.getValue();
            if (p.isEnabled()) {
                all.add(new RateLimitPolicy(p.policyKey(), p.getReplenishRate(), p.getBurstCapacity(), p.getRequestedTokens()));
            }
        }
        all.add(fallback());
        return all;
    }
}
