package com.eventtick.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Binds the Phase 12 policy matrix from {@code eventtick.rate-limit.*} in
 * {@code application.yml} — a dedicated namespace, not
 * {@code spring.cloud.gateway.redis-rate-limiter.*} (Phase 11's), because
 * that one only has room for a single flat policy and has no structure for
 * "many named policies"; nothing reads the old Phase 11 property names
 * anymore (retired, not left dangling — see the README/architecture docs).
 *
 * <p>Shape: {@code policies.<CATEGORY>.<TIER>.{replenish-rate,burst-capacity,requested-tokens}}
 * for whichever (category, tier) combinations are actually meaningful (see
 * {@code application.yml} for the full matrix and the reasoning behind each
 * number), plus one {@code fallback} policy used whenever a specific
 * combination has not been configured — most notably {@link RequestCategory#UNKNOWN}
 * (an unrecognized path), but also anything else not explicitly listed, so a
 * gap in the matrix degrades to a conservative limit rather than either
 * crashing or silently allowing unlimited requests.
 */
@ConfigurationProperties("eventtick.rate-limit")
public class RateLimitPolicyProperties {

    private Map<RequestCategory, Map<UserTier, PolicyValues>> policies = new EnumMap<>(RequestCategory.class);

    private PolicyValues fallback = new PolicyValues();

    public Map<RequestCategory, Map<UserTier, PolicyValues>> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<RequestCategory, Map<UserTier, PolicyValues>> policies) {
        this.policies = policies;
    }

    public PolicyValues getFallback() {
        return fallback;
    }

    public void setFallback(PolicyValues fallback) {
        this.fallback = fallback;
    }

    /** The three numbers a {@code RedisRateLimiter.Config} needs, before an id is attached. */
    public static class PolicyValues {

        private int replenishRate = 1;
        private int burstCapacity = 1;
        /** 1 by default: a plain per-request limit unless a policy deliberately weights some calls more than others. */
        private int requestedTokens = 1;

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(int requestedTokens) {
            this.requestedTokens = requestedTokens;
        }
    }
}
