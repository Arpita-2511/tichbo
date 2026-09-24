/**
 * Redis-backed request rate limiting at the gateway (Phase 11): the initial,
 * intentionally simple static policy. See
 * {@link com.eventtick.gateway.ratelimit.RateLimitingGlobalFilter} for the
 * filter itself and {@link com.eventtick.gateway.ratelimit.RateLimitKeyResolver}
 * for how a request is attributed to a bucket. The actual token-bucket
 * algorithm and its Redis state are Spring Cloud Gateway's own
 * {@code RedisRateLimiter} (auto-configured once
 * {@code spring-boot-starter-data-redis-reactive} is on the classpath) —
 * nothing here reimplements it.
 *
 * <p>Dynamic, per-plan, or admin-configurable policies are explicitly out of
 * scope here; see {@code docs/architecture.md} §"Rate Limiting" for what
 * Phase 12 is expected to add on top of this.
 */
package com.eventtick.gateway.ratelimit;
