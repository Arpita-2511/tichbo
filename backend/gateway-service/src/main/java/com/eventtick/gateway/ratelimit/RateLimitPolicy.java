package com.eventtick.gateway.ratelimit;

/**
 * One resolved rate-limit policy: the numbers a
 * {@link org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter}
 * needs, plus the {@code id} it is registered under (see
 * {@code RateLimitingGlobalFilter#registerPolicies}) and charged against.
 *
 * <p>{@code id} doubles as the prefix of the actual Redis key (see
 * {@code RateLimitingGlobalFilter} for why: {@code RedisRateLimiter} builds
 * its Redis key from the resolved caller identity alone, not from the policy
 * id passed to {@code isAllowed}, so two different policies applied to the
 * same identity would otherwise silently share one bucket).
 */
record RateLimitPolicy(String id, int replenishRate, int burstCapacity, int requestedTokens) {
}
