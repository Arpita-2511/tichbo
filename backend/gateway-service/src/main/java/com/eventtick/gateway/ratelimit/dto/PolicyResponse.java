package com.eventtick.gateway.ratelimit.dto;

import com.eventtick.gateway.ratelimit.DynamicRateLimitPolicy;

import java.time.Instant;

public record PolicyResponse(
        String id,
        String category,
        String tier,
        int replenishRate,
        int burstCapacity,
        int requestedTokens,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static PolicyResponse from(DynamicRateLimitPolicy p) {
        return new PolicyResponse(p.getId(), p.getCategory(), p.getTier(),
                p.getReplenishRate(), p.getBurstCapacity(), p.getRequestedTokens(),
                p.isEnabled(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
