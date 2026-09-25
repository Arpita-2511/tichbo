package com.eventtick.gateway.ratelimit.dto;

/**
 * FR-40: one policy's numbers, for {@code GET /api/admin/rate-limits/stats}.
 * A public API DTO — deliberately not the package-private
 * {@code RateLimitPolicy}/{@code RateLimitPolicyProperties.PolicyValues}
 * internal types, the same "response shape is not the domain type"
 * convention every other service in this project already follows.
 */
public record RateLimitPolicyDto(int replenishRate, int burstCapacity, int requestedTokens) {
}
