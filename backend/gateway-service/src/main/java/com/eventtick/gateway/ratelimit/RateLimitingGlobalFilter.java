package com.eventtick.gateway.ratelimit;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import com.eventtick.gateway.filter.RequestIdWebFilter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Phase 11: static, Redis-backed rate limiting. Runs as a
 * {@link GlobalFilter}, so — unlike the security {@code WebFilter}s — it only
 * ever sees a request that has already cleared JWT authentication for a
 * protected route, or been explicitly permitted for a public one: exactly
 * the {@code JWT authentication -> Rate limiter -> Route} order the
 * architecture calls for, with no extra wiring needed to get it, because
 * Spring Security's {@code WebFilter} chain always finishes before a request
 * reaches gateway routing (where every {@link GlobalFilter} runs).
 *
 * <p>The algorithm and its shared state are entirely
 * {@link RedisRateLimiter} (Spring Cloud Gateway's own token-bucket
 * implementation, run as one atomic Lua script in Redis) — this class only
 * decides the bucket key ({@link RateLimitKeyResolver}), which policy id to
 * charge against, and what a caller sees when the limiter says no. Because
 * the bucket lives in Redis rather than in this process's memory, multiple
 * gateway instances pointed at the same Redis correctly share one limit per
 * key instead of each instance granting its own separate allowance.
 *
 * <p><b>Policy:</b> {@link #POLICY_ID} is a single fixed id, so — for this
 * first, intentionally simple phase — there is one shared numeric policy
 * (read from {@code spring.cloud.gateway.redis-rate-limiter.*}) for every
 * route, with buckets separated only by <i>who</i> is calling
 * ({@code RateLimitKeyResolver}), not by which route or plan. Per-route or
 * per-plan policies are Phase 12 (dynamic rate limiting) territory.
 *
 * <p><b>If Redis itself is unreachable or errors</b>, {@link RedisRateLimiter}
 * fails open — the request is treated as allowed — rather than this filter
 * turning a Redis outage into a full API outage; see
 * {@code docs/architecture.md} §"Rate Limiting" for why that trade-off was
 * chosen, and {@code spring.data.redis.timeout}/{@code connect-timeout} in
 * {@code application.yml} for why that failure is fast, not a hang.
 */
@Component
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    /**
     * The one bucket namespace this initial policy uses for every route.
     * Combined with the resolved key (e.g. {@code user:<uuid>} or
     * {@code ip:<address>}) to form the actual Redis key.
     */
    static final String POLICY_ID = "gateway";

    private static final Logger log = LoggerFactory.getLogger(RateLimitingGlobalFilter.class);

    private final RedisRateLimiter rateLimiter;
    private final RateLimitKeyResolver keyResolver;
    private final GatewayErrorWriter errors;

    // Bound from the same spring.cloud.gateway.redis-rate-limiter.* properties
    // GatewayRedisAutoConfiguration itself uses for RedisRateLimiter's default
    // config — one source of truth in application.yml, read a second time
    // only because #registerPolicy needs the values, not because the policy
    // can differ from the documented default.
    private final int replenishRate;
    private final int burstCapacity;
    private final int requestedTokens;

    public RateLimitingGlobalFilter(RedisRateLimiter rateLimiter, RateLimitKeyResolver keyResolver, GatewayErrorWriter errors,
            @Value("${spring.cloud.gateway.redis-rate-limiter.replenish-rate}") int replenishRate,
            @Value("${spring.cloud.gateway.redis-rate-limiter.burst-capacity}") int burstCapacity,
            @Value("${spring.cloud.gateway.redis-rate-limiter.requested-tokens}") int requestedTokens) {
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.errors = errors;
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;
        this.requestedTokens = requestedTokens;
    }

    /**
     * {@link RedisRateLimiter#isAllowed(String, String)} requires a
     * {@link RedisRateLimiter.Config} to already be registered for whatever
     * route id is passed in — normally done by the declarative
     * {@code RequestRateLimiter=} route filter as it applies to each route,
     * which this class deliberately does not use (see the class Javadoc). So
     * {@link #POLICY_ID} is registered once here, directly on the limiter's
     * own (mutable, shared) config map — the one config entry every call to
     * {@link #filter} then resolves.
     */
    @PostConstruct
    void registerPolicy() {
        rateLimiter.getConfig().put(POLICY_ID, new RedisRateLimiter.Config()
                .setReplenishRate(replenishRate)
                .setBurstCapacity(burstCapacity)
                .setRequestedTokens(requestedTokens));
    }

    @Override
    public int getOrder() {
        // Comfortably ahead of NettyRoutingFilter (which actually forwards the
        // request and runs near Ordered.LOWEST_PRECEDENCE), and there is
        // nothing before it that this filter depends on.
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return keyResolver.resolve(exchange)
                .flatMap(key -> rateLimiter.isAllowed(POLICY_ID, key))
                .flatMap(response -> respond(exchange, chain, response));
    }

    private Mono<Void> respond(ServerWebExchange exchange, GatewayFilterChain chain, RateLimiter.Response response) {
        ServerHttpResponse httpResponse = exchange.getResponse();
        // The conventional (not IETF-standardized) X-RateLimit-* headers
        // RedisRateLimiter itself already builds: remaining tokens, the
        // configured replenish rate, and burst capacity. Added to every
        // response, allowed or not, so a well-behaved client can back off
        // before it ever gets a 429.
        response.getHeaders().forEach((name, value) -> httpResponse.getHeaders().add(name, value));

        if (response.isAllowed()) {
            return chain.filter(exchange);
        }

        log.info("rate limit exceeded id={} path={}",
                RequestIdWebFilter.requestId(exchange), exchange.getRequest().getPath().value());
        return errors.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "Too many requests. Please slow down and try again shortly.");
    }
}
