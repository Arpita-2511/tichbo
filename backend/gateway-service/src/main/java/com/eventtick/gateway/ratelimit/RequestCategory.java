package com.eventtick.gateway.ratelimit;

/**
 * The four kinds of request the gateway's routes distinguish, plus a
 * catch-all for anything else. Deliberately based on the request path, not
 * on the Spring Cloud Gateway route id that matched — path classification is
 * testable as a pure function and stays correct even if routes are
 * restructured, renamed, or (for {@code AUTH}) partially unauthenticated.
 *
 * @see RequestCategoryClassifier
 */
public enum RequestCategory {

    /** {@code /api/auth/**} — includes the public register/login endpoints. */
    AUTH,

    /** {@code /api/catalog/**} — content, venues, shows. */
    CATALOG,

    /** {@code /api/bookings/**}. */
    BOOKING,

    /** {@code /api/users/**} — the authenticated user's own account. */
    USER,

    /**
     * Anything not matching one of the above. Not the same as "no route
     * matched at all" (that is a 404 before this ever runs) — this is for a
     * path that resolves to nothing this classifier recognizes, e.g. if a
     * new route is added to {@code application.yml} without updating
     * {@link RequestCategoryClassifier}. Always resolves to the fallback
     * policy (see {@code RateLimitPolicyResolver}), never to "no limit".
     */
    UNKNOWN
}
