package com.eventtick.gateway.ratelimit;

/**
 * The identity dimension of a dynamic rate-limit policy — independent of
 * {@link RequestCategory} (the "what"), this is the "who".
 *
 * <p><b>{@code ADMIN} is a role, {@code FREE}/{@code PRO}/{@code PREMIUM}
 * are plans</b> — two independent columns on the User Service's
 * {@code users} table ({@code role} and {@code plan_id}), not one. An
 * account can in principle be an admin on any plan. This enum treats them as
 * one axis anyway, because that is what a rate-limit *policy* needs — one
 * number per caller — and {@link RateLimitPolicyResolver} documents exactly
 * how the two source claims collapse into it (role checked first: an
 * authenticated {@code ADMIN} always resolves to {@code ADMIN} here,
 * regardless of their plan).
 */
public enum UserTier {

    /** No validated JWT on the request (the public auth endpoints, or anything else unauthenticated). */
    PUBLIC,

    /** Authenticated, {@code role=CUSTOMER}, and either {@code plan=Free} or an unrecognized/missing plan claim (the safe default). */
    FREE,

    /** Authenticated, {@code role=CUSTOMER}, {@code plan=Pro}. */
    PRO,

    /** Authenticated, {@code role=CUSTOMER}, {@code plan=Premium}. */
    PREMIUM,

    /** Authenticated, {@code role=ADMIN} — takes precedence over whatever plan the account also has. */
    ADMIN
}
