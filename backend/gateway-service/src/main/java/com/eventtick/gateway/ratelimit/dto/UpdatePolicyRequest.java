package com.eventtick.gateway.ratelimit.dto;

public record UpdatePolicyRequest(
        int replenishRate,
        int burstCapacity,
        int requestedTokens,
        boolean enabled
) {
}
