package com.eventtick.gateway.ratelimit;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 12: picks which {@link RateLimitPolicy} applies to a request,
 * instead of Phase 11's single static one.
 *
 * <p><b>Resolution precedence</b> — two independent inputs, category and
 * tier, are each resolved on their own and then looked up together:
 * <ol>
 *   <li><b>Request category</b> ({@link RequestCategoryClassifier}, from the
 *   path alone) — {@link RequestCategory#UNKNOWN} short-circuits straight to
 *   the fallback policy; there is no per-tier variant of "we don't recognize
 *   this endpoint".
 *   <li><b>Authentication state</b> — no validated JWT -> {@link UserTier#PUBLIC}.
 *   <li><b>Role</b> — {@code role=ADMIN} -> {@link UserTier#ADMIN}, checked
 *   <i>before</i> plan (see {@link UserTier}'s Javadoc for why role and plan
 *   are independent, and {@link UserTierResolver} for the exact claim
 *   handling).
 *   <li><b>Plan</b> — otherwise, the {@code plan} claim, defaulting to
 *   {@link UserTier#FREE} if missing or unrecognized.
 *   <li><b>Fallback</b> — if the resolved (category, tier) pair has no
 *   configured policy (most of today's matrix only defines
 *   {@code AUTH -> PUBLIC} and {@code CATALOG/BOOKING/USER -> FREE/PRO/PREMIUM/ADMIN};
 *   see {@code application.yml} for exactly which combinations exist and
 *   why the rest are intentionally absent), the one configured
 *   {@code eventtick.rate-limit.fallback} policy is used. This is a
 *   deliberately conservative, always-defined policy — a gap in the matrix
 *   degrades to a strict limit, never to "no limit".
 * </ol>
 */
@Component
class RateLimitPolicyResolver {

    static final String FALLBACK_POLICY_ID = "FALLBACK";

    private final RateLimitPolicyProperties properties;
    private final UserTierResolver tierResolver;

    RateLimitPolicyResolver(RateLimitPolicyProperties properties, UserTierResolver tierResolver) {
        this.properties = properties;
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
        RateLimitPolicyProperties.PolicyValues values = valuesFor(category, tier);
        if (values == null) {
            return fallback();
        }
        return toPolicy(category + ":" + tier, values);
    }

    private RateLimitPolicyProperties.PolicyValues valuesFor(RequestCategory category, UserTier tier) {
        Map<UserTier, RateLimitPolicyProperties.PolicyValues> byTier = properties.getPolicies().get(category);
        return byTier == null ? null : byTier.get(tier);
    }

    private RateLimitPolicy fallback() {
        return toPolicy(FALLBACK_POLICY_ID, properties.getFallback());
    }

    private static RateLimitPolicy toPolicy(String id, RateLimitPolicyProperties.PolicyValues values) {
        return new RateLimitPolicy(id, values.getReplenishRate(), values.getBurstCapacity(), values.getRequestedTokens());
    }

    /**
     * Every policy that must be registered on the {@code RedisRateLimiter}
     * at startup (see {@code RateLimitingGlobalFilter#registerPolicies}) —
     * everything actually configured in the matrix, plus the fallback, which
     * always exists even if nothing in {@code policies.*} does.
     */
    List<RateLimitPolicy> allConfiguredPolicies() {
        List<RateLimitPolicy> all = new ArrayList<>();
        properties.getPolicies().forEach((category, byTier) ->
                byTier.forEach((tier, values) -> all.add(toPolicy(category + ":" + tier, values))));
        all.add(fallback());
        return all;
    }
}
