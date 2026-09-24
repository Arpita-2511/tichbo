package com.eventtick.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Decides which rate-limit bucket a request belongs to, per the flow
 * {@code Client -> Gateway -> JWT authentication -> Rate limiter -> Route}:
 * by the time this runs (inside gateway routing, after Spring Security's
 * filter chain has already completed for the request), the reactive
 * security context already holds whatever the JWT layer established.
 *
 * <ul>
 *   <li><b>Authenticated</b> (the request carried a JWT that Phase 7.3
 *   validated) — keyed by the token's {@code sub} claim, so each user has
 *   their own bucket regardless of which device or IP they call from.
 *   <li><b>Everyone else</b> — public endpoints
 *   ({@code POST /api/auth/register}/{@code login}), and any other request
 *   Spring Security did not attach a validated JWT to — keyed by the caller's
 *   TCP source address, <b>not</b> a client-supplied header. A client can put
 *   anything in {@code X-Forwarded-For}; trusting it would let an attacker
 *   pick a fresh bucket for every request. There is no trusted reverse proxy
 *   in front of the gateway yet in this local-development setup, so the
 *   socket address is exactly the real caller.
 * </ul>
 *
 * Implements Spring Cloud Gateway's own {@link KeyResolver} so this stays
 * usable from the declarative {@code RequestRateLimiter=} route filter too,
 * even though {@link RateLimitingGlobalFilter} calls it directly.
 */
@Component
public class RateLimitKeyResolver implements KeyResolver {

    static final String AUTHENTICATED_KEY_PREFIX = "user:";
    static final String ANONYMOUS_KEY_PREFIX = "ip:";
    static final String UNKNOWN_ADDRESS = "unknown";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                // Anonymous requests also get an Authentication (Spring Security's
                // default AnonymousAuthenticationToken, which reports
                // isAuthenticated() == true) — only a real validated JWT counts here.
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(authentication -> AUTHENTICATED_KEY_PREFIX + authentication.getName())
                .switchIfEmpty(Mono.fromSupplier(() -> ANONYMOUS_KEY_PREFIX + clientAddress(exchange)));
    }

    private static String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            // Not expected for a real socket request; falls back to one shared
            // bucket rather than failing the request.
            return UNKNOWN_ADDRESS;
        }
        return remote.getAddress().getHostAddress();
    }
}
