package com.eventtick.gateway.ratelimit.dto;

public record CreatePolicyRequest(
        String category,
        String tier,
        int replenishRate,
        int burstCapacity,
        int requestedTokens
) {
}
