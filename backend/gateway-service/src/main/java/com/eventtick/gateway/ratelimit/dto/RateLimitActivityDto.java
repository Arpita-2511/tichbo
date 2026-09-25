package com.eventtick.gateway.ratelimit.dto;

/**
 * FR-40: allowed/rejected counts for one policy id, since this gateway
 * instance's counters were initialized — not a sliding window, not
 * historical analytics (see {@code RateLimitActivityRecorder}).
 */
public record RateLimitActivityDto(long allowed, long rejected) {
}
