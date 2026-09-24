package com.eventtick.gateway.ratelimit;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves a request's {@link UserTier} from the <b>validated</b> JWT
 * already sitting in the reactive security context — never from a
 * client-supplied header, query parameter, or request body field. A request
 * that reaches this class either carried a JWT Phase 7.3 already verified
 * (signature, issuer, audience, expiry, structural {@code sub}/{@code role}
 * checks — see {@code GatewayJwtDecoder}), or it did not authenticate at
 * all; there is no third option, so nothing here can be spoofed by sending
 * e.g. {@code X-Plan: PRO}.
 *
 * <p><b>Resolution order</b> (see also {@code RateLimitPolicyResolver}'s
 * class Javadoc for the full precedence across category and tier together):
 * <ol>
 *   <li>no {@link JwtAuthenticationToken} in the security context ->
 *   {@link UserTier#PUBLIC};
 *   <li>{@code role} claim is {@code ADMIN} -> {@link UserTier#ADMIN},
 *   regardless of the {@code plan} claim — role is checked before plan
 *   because it represents operational trust, not purchased capacity, and
 *   the two are independent columns in the User Service's data model (see
 *   {@link UserTier}'s Javadoc);
 *   <li>otherwise, the {@code plan} claim (case-insensitively) against the
 *   plans that actually exist in {@code plans} table today (see
 *   {@code database/migrations/0003_seed_plans.up.sql}: Free, Pro, Premium)
 *   -> the matching tier;
 *   <li>a missing, blank, or unrecognized {@code plan} claim ->
 *   {@link UserTier#FREE}. The decoder does not structurally validate this
 *   claim's shape (only {@code role} and {@code sub} are), so it must be
 *   treated as untrusted-in-shape even though it is signed: an unknown value
 *   never silently grants a higher tier than the safest one.
 * </ol>
 */
@Component
public class UserTierResolver {

    private static final Map<String, UserTier> KNOWN_PLANS = Map.of(
            "free", UserTier.FREE,
            "pro", UserTier.PRO,
            "premium", UserTier.PREMIUM);

    Mono<UserTier> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> tierOf(token.getToken()))
                .switchIfEmpty(Mono.just(UserTier.PUBLIC));
    }

    private static UserTier tierOf(Jwt jwt) {
        if ("ADMIN".equals(jwt.getClaimAsString("role"))) {
            return UserTier.ADMIN;
        }
        String plan = jwt.getClaimAsString("plan");
        if (plan == null) {
            return UserTier.FREE;
        }
        return KNOWN_PLANS.getOrDefault(plan.trim().toLowerCase(Locale.ROOT), UserTier.FREE);
    }
}
