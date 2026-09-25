package com.eventtick.gateway.ratelimit.dto;

import java.util.Map;

/**
 * FR-40: the activity half of {@code GET /api/admin/rate-limits/stats}.
 * {@code redisAvailable = false} means the counters could not be read (a
 * Redis outage) — {@code byPolicy} is then empty rather than the endpoint
 * failing; see {@code RateLimitActivityRecorder#readActivitySection}.
 */
public record RateLimitActivitySection(boolean redisAvailable, Map<String, RateLimitActivityDto> byPolicy) {
}
