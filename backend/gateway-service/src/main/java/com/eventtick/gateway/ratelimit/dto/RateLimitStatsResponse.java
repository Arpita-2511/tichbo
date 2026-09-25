package com.eventtick.gateway.ratelimit.dto;

import java.util.Map;

/**
 * FR-40: response for {@code GET /api/admin/rate-limits/stats}. {@code policies}
 * is keyed {@code category -> tier -> policy}, mirroring
 * {@code RateLimitPolicyProperties}'s own shape exactly (that bean is the
 * only source for this section — nothing here is hardcoded).
 */
public record RateLimitStatsResponse(
        Map<String, Map<String, RateLimitPolicyDto>> policies,
        RateLimitPolicyDto fallback,
        RateLimitActivitySection activity
) {
}
