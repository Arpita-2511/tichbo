package com.eventtick.gateway.ratelimit;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import com.eventtick.gateway.filter.RequestIdWebFilter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Phase 12: dynamic, Redis-backed rate limiting.
 *
 * <p><b>Why a plain {@code WebFilter} and not a Spring Cloud Gateway
 * {@link GlobalFilter}</b> (Phase 11 originally used one — this was found and
 * fixed while adding a test for the "unknown path" fallback, see
 * {@code docs/architecture.md}'s Phase 12 section): a {@code GlobalFilter}
 * only runs once a route has actually matched — Spring Cloud Gateway's
 * {@code RoutePredicateHandlerMapping} returns no handler at all for a path
 * that matches nothing, so the {@code FilteringWebHandler} that invokes every
 * {@code GlobalFilter} never runs either. A {@code GlobalFilter}-based
 * limiter therefore never even sees requests to an unrecognized path — the
 * exact case the fallback policy exists for — and would let them bypass
 * rate limiting entirely rather than fall back to it. This is the same
 * reasoning {@code RequestIdWebFilter} (Phase 7.4) already used for the
 * analogous problem with 401s and 404s.
 *
 * <p>Ordering: must run <i>after</i> Spring Security's reactive filter chain
 * (registered at {@code WebFluxSecurityConfiguration.WEB_FILTER_CHAIN_FILTER_ORDER
 * = -100}), so the security context it reads is already populated — but,
 * unlike {@code RequestIdWebFilter}, must not run before it. {@link #getOrder()}
 * uses {@code -50}: after security, comfortably before routing/business
 * concerns, satisfying the architecture's
 * {@code JWT authentication -> Dynamic policy resolution -> Rate limiter ->
 * Route} order without any extra wiring.
 *
 * <p>The algorithm and its shared state are entirely {@link RedisRateLimiter}
 * (Spring Cloud Gateway's own token-bucket implementation, run as one atomic
 * Lua script in Redis) — nothing here reimplements it. This class decides
 * <i>which</i> policy applies ({@link RateLimitPolicyResolver}, replacing
 * Phase 11's single fixed one), the bucket key ({@link RateLimitKeyResolver}
 * for the caller's identity, composed with the policy below), and what a
 * caller sees when the limiter says no. Because the bucket lives in Redis
 * rather than in this process's memory, multiple gateway instances pointed
 * at the same Redis correctly share one limit per key instead of each
 * instance granting its own separate allowance.
 *
 * <p><b>Registering multiple policies with the native {@code RedisRateLimiter}:</b>
 * {@link RedisRateLimiter#isAllowed(String, String)} requires a
 * {@link RedisRateLimiter.Config} to already be registered under whatever id
 * is passed as its first argument (confirmed by reading Spring Cloud
 * Gateway 4.1.5's source: {@code loadConfiguration} does
 * {@code getConfig().getOrDefault(routeId, defaultConfig)}, and the
 * {@code ConfigurationService}-built bean this app uses never populates
 * {@code defaultConfig}) — normally done by the declarative
 * {@code RequestRateLimiter=} route filter, which this class deliberately
 * does not use (its 429 has no body, which conflicts with the JSON shape
 * every other gateway error uses). So every policy
 * {@link RateLimitPolicyResolver} can resolve is registered once, up front,
 * directly on the limiter's own (mutable, shared) config map — the same
 * technique Phase 11 used for its one policy, just for many.
 *
 * <p><b>Bucket key design:</b> {@link RedisRateLimiter#isAllowed} uses its
 * first argument (the policy id) <i>only</i> to choose a {@code Config}; the
 * actual Redis key is built from the <i>second</i> argument alone (Spring
 * Cloud Gateway's {@code getKeys(id)}: {@code request_rate_limiter.{id}.tokens}).
 * That means passing just {@code user:<uuid>} for every policy would let two
 * different policies applied to the same caller (e.g. a CATALOG request and
 * a BOOKING request from the same user) silently share one bucket. To
 * prevent that, the id actually passed to {@code isAllowed} is
 * {@code <policyId>:<identity>} — the resolved policy id doubles as a Redis
 * key prefix, so FREE-catalog, PRO-catalog, and FREE-booking for the same
 * user are three separate buckets, never one.
 *
 * <p><b>If Redis itself is unreachable or errors</b>, {@link RedisRateLimiter}
 * fails open — the request is treated as allowed — rather than this filter
 * turning a Redis outage into a full API outage; unchanged from Phase 11.
 */
@Component
public class RateLimitingGlobalFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingGlobalFilter.class);

    private final RedisRateLimiter rateLimiter;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final GatewayErrorWriter errors;

    public RateLimitingGlobalFilter(RedisRateLimiter rateLimiter, RateLimitKeyResolver keyResolver,
            RateLimitPolicyResolver policyResolver, GatewayErrorWriter errors) {
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.policyResolver = policyResolver;
        this.errors = errors;
    }

    /**
     * Registers every policy {@link RateLimitPolicyResolver} can resolve —
     * see the class Javadoc for why this is required at all. Done once, up
     * front, rather than lazily on first use, so a request is never the
     * first thing to discover a matrix entry is missing.
     */
    @PostConstruct
    void registerPolicies() {
        policyResolver.allConfiguredPolicies().forEach(policy ->
                rateLimiter.getConfig().put(policy.id(), new RedisRateLimiter.Config()
                        .setReplenishRate(policy.replenishRate())
                        .setBurstCapacity(policy.burstCapacity())
                        .setRequestedTokens(policy.requestedTokens())));
    }

    @Override
    public int getOrder() {
        // After Spring Security's chain (-100, see class Javadoc), so the
        // security context is already populated; still well ahead of routing.
        return -50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.zip(policyResolver.resolve(exchange), keyResolver.resolve(exchange))
                .flatMap(resolved -> {
                    RateLimitPolicy policy = resolved.getT1();
                    String identity = resolved.getT2();
                    // See the class Javadoc, "Bucket key design": the policy id
                    // must be part of the Redis key itself, not just the
                    // isAllowed() lookup argument, or different policies for the
                    // same caller would collide.
                    return rateLimiter.isAllowed(policy.id(), policy.id() + ":" + identity)
                            .flatMap(response -> respond(exchange, chain, policy, response));
                });
    }

    private Mono<Void> respond(ServerWebExchange exchange, WebFilterChain chain, RateLimitPolicy policy, RateLimiter.Response response) {
        ServerHttpResponse httpResponse = exchange.getResponse();
        // The conventional (not IETF-standardized) X-RateLimit-* headers
        // RedisRateLimiter itself already builds: remaining tokens, and the
        // replenish rate/burst capacity of whichever policy actually applied
        // to this request. Added to every response, allowed or not, so a
        // well-behaved client can back off before it ever gets a 429.
        response.getHeaders().forEach((name, value) -> httpResponse.getHeaders().add(name, value));

        if (response.isAllowed()) {
            return chain.filter(exchange);
        }

        log.info("rate limit exceeded id={} path={} policy={}",
                RequestIdWebFilter.requestId(exchange), exchange.getRequest().getPath().value(), policy.id());
        return errors.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "Too many requests. Please slow down and try again shortly.");
    }
}
