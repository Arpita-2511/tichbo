package com.eventtick.gateway.ratelimit;

import com.eventtick.gateway.ratelimit.dto.RateLimitActivityDto;
import com.eventtick.gateway.ratelimit.dto.RateLimitActivitySection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FR-40: observational-only allowed/rejected counters for
 * {@code GET /api/admin/rate-limits/stats}, recorded from
 * {@link RateLimitingGlobalFilter#respond} — the one place that already
 * sees every rate-limit decision.
 *
 * <p><b>A completely separate Redis namespace from {@link org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter}'s
 * own keys.</b> {@code RedisRateLimiter}'s token-bucket state
 * ({@code request_rate_limiter.{id}.tokens}/{@code .timestamp}) is an
 * undocumented Spring Cloud Gateway implementation detail — this class
 * never reads, scans, or otherwise depends on it. Everything here lives
 * under its own prefix, {@value #NAMESPACE}, written and read only by this
 * class.
 *
 * <p><b>Counters represent "since this gateway instance's counters were
 * initialized"</b> — not a sliding window, not historical analytics, not a
 * permanent audit record. They reset on restart; no TTL, no time-bucketing.
 * This is a deliberately minimal reading of FR-40's "current traffic/
 * rate-limit activity", consistent with the approved design.
 *
 * <p><b>Writes never block and never fail the request they're recording.</b>
 * {@link #recordAllowed}/{@link #recordRejected} fire the Redis
 * {@code INCR} independently of the caller's reactive chain (a detached
 * subscription) — {@link RateLimitingGlobalFilter#respond} does not wait
 * for it, and any Redis error is logged and swallowed here, never
 * propagated back to the real request.
 *
 * <p><b>Reads degrade gracefully.</b> {@link #readActivitySection} never
 * errors: on any Redis failure it resolves to
 * {@code redisAvailable = false} with an empty {@code byPolicy} map, so
 * {@code GET /api/admin/rate-limits/stats} can still return {@code 200}
 * with the (Redis-independent) policy matrix.
 */
@Component
public class RateLimitActivityRecorder {

    private static final Logger log = LoggerFactory.getLogger(RateLimitActivityRecorder.class);

    static final String NAMESPACE = "gateway:admin:rate-limit-activity:";
    static final String ALLOWED_SUFFIX = ":allowed";
    static final String REJECTED_SUFFIX = ":rejected";

    private final ReactiveStringRedisTemplate redis;

    public RateLimitActivityRecorder(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Records one allowed request against {@code policyId}. Fire-and-forget; see class Javadoc. */
    public void recordAllowed(String policyId) {
        increment(allowedKey(policyId));
    }

    /** Records one rejected (429) request against {@code policyId}. Fire-and-forget; see class Javadoc. */
    public void recordRejected(String policyId) {
        increment(rejectedKey(policyId));
    }

    private void increment(String key) {
        redis.opsForValue().increment(key)
                .doOnError(e -> log.warn("rate-limit activity: failed to record {}: {}", key, e.toString()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    /**
     * Reads current allowed/rejected counts for exactly the given policy
     * ids (the full configured matrix, including the fallback — the caller
     * is expected to pass the complete, known set, since there is no index
     * of "every id ever used" in Redis to enumerate instead).
     */
    public Mono<RateLimitActivitySection> readActivitySection(Collection<String> policyIds) {
        return Flux.fromIterable(policyIds)
                .flatMap(id -> readOne(id).map(dto -> Map.entry(id, dto)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new)
                .map(byPolicy -> new RateLimitActivitySection(true, byPolicy))
                .onErrorResume(e -> {
                    log.warn("rate-limit activity: Redis unavailable while reading stats: {}", e.toString());
                    return Mono.just(new RateLimitActivitySection(false, Map.of()));
                });
    }

    private Mono<RateLimitActivityDto> readOne(String policyId) {
        return Mono.zip(
                redis.opsForValue().get(allowedKey(policyId)).defaultIfEmpty("0"),
                redis.opsForValue().get(rejectedKey(policyId)).defaultIfEmpty("0")
        ).map(counts -> new RateLimitActivityDto(parse(counts.getT1()), parse(counts.getT2())));
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String allowedKey(String policyId) {
        return NAMESPACE + policyId + ALLOWED_SUFFIX;
    }

    private static String rejectedKey(String policyId) {
        return NAMESPACE + policyId + REJECTED_SUFFIX;
    }
}
