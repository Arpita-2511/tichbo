package com.eventtick.gateway.ratelimit;

/**
 * Maps a request path to a {@link RequestCategory} by simple, explicit
 * prefix matching — mirrors the path predicates in {@code application.yml}'s
 * gateway routes, but is independent of them: a pure function of the path
 * string, so it is trivially unit-testable and does not depend on which
 * Spring Cloud Gateway route (if any) actually matched.
 */
final class RequestCategoryClassifier {

    private RequestCategoryClassifier() {
    }

    static RequestCategory classify(String path) {
        if (path.startsWith("/api/auth/")) {
            return RequestCategory.AUTH;
        }
        if (path.startsWith("/api/catalog/")) {
            return RequestCategory.CATALOG;
        }
        if (path.startsWith("/api/bookings/") || path.equals("/api/bookings")) {
            return RequestCategory.BOOKING;
        }
        if (path.startsWith("/api/users/") || path.equals("/api/users")) {
            return RequestCategory.USER;
        }
        if (path.startsWith("/api/payments/") || path.equals("/api/payments")) {
            return RequestCategory.PAYMENT;
        }
        if (path.startsWith("/api/admin/")) {
            return RequestCategory.ADMIN;
        }
        return RequestCategory.UNKNOWN;
    }
}
