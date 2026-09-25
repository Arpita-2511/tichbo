package com.eventtick.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.1 (plus the Phase 13.5.3/13.6.3/13.7.1/13.7.2/FR-37/FR-38 admin
 * routes, most recently {@code admin-show-by-id}): every route exists, each
 * request path lands on the right upstream, and nothing rewrites the path
 * (no filters on any route). Does not start the upstream services or send
 * real traffic — it asks the gateway's own route table which route a given
 * path would match. See {@code GatewayAdminAuthorizationTest} for whether a
 * caller is actually *allowed* through to a route — this class only checks
 * where a path is routed, never who is authorized.
 */
@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    private List<Route> routes() {
        return routeLocator.getRoutes().collectList().block();
    }

    private Optional<Route> matchFor(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return routes().stream()
                .filter(route -> Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block()))
                .findFirst();
    }

    private void assertRoutedTo(String path, String routeId, String upstream) {
        Optional<Route> match = matchFor(path);
        assertThat(match).as("route for %s", path).isPresent();
        assertThat(match.get().getId()).isEqualTo(routeId);
        assertThat(match.get().getUri().toString()).isEqualTo(upstream);
    }

    @Test
    void exactlyFifteenRoutesAreRegistered() {
        assertThat(routes()).extracting(Route::getId)
                .containsExactlyInAnyOrder("user-service", "catalog-service", "booking-service",
                        "admin-content", "admin-content-by-id", "admin-show-cancel", "admin-shows",
                        "admin-show-by-id", "admin-venues", "admin-venue-by-id", "admin-bookings",
                        "admin-show-seat-activity", "admin-users", "admin-users-plan", "admin-users-role");
    }

    @Test
    void authAndUsersPaths_goToUserService() {
        assertRoutedTo("/api/auth/login", "user-service", "http://localhost:8081");
        assertRoutedTo("/api/auth/register", "user-service", "http://localhost:8081");
        assertRoutedTo("/api/users/me", "user-service", "http://localhost:8081");
    }

    @Test
    void catalogPaths_goToCatalogService() {
        assertRoutedTo("/api/catalog/content", "catalog-service", "http://localhost:8082");
        assertRoutedTo("/api/catalog/shows/123", "catalog-service", "http://localhost:8082");
    }

    @Test
    void bookingPaths_goToBookingService() {
        assertRoutedTo("/api/bookings", "booking-service", "http://localhost:8083");
        assertRoutedTo("/api/bookings/shows/123/seats", "booking-service", "http://localhost:8083");
    }

    @Test
    void adminContentPath_goesToCatalogService() {
        // Phase 13.5.3: an explicit, narrow route for exactly this endpoint
        // — not part of the catalog-service Path=/api/catalog/** predicate
        // (a different URL entirely) and not a generic /api/admin/** rule.
        assertRoutedTo("/api/admin/content", "admin-content", "http://localhost:8082");
    }

    @Test
    void adminContentRoute_matchesOnlyItsExactPath_notASubPathOrAnotherAdminEndpoint() {
        // No "/**" on this predicate: it is deliberately this one path,
        // not a catch-all. /api/admin/content/extra is now a real route of
        // its own (admin-content-by-id, FR-38) rather than unrouted, and
        // /api/admin/users is a real route of its own (FR-37 fix) — this
        // only asserts neither is *this* route.
        assertThat(matchFor("/api/admin/content/extra")).isNotEmpty();
        assertThat(matchFor("/api/admin/content/extra").get().getId()).isEqualTo("admin-content-by-id");
        assertThat(matchFor("/api/admin/users")).isNotEmpty();
        assertThat(matchFor("/api/admin/users").get().getId()).isEqualTo("admin-users");
    }

    @Test
    void adminContentByIdPath_goesToCatalogService() {
        // FR-38: PUT/DELETE /api/admin/content/{id}. A Path predicate
        // doesn't distinguish HTTP method, so one route serves both.
        assertRoutedTo("/api/admin/content/123e4567-e89b-12d3-a456-426614174000",
                "admin-content-by-id", "http://localhost:8082");
    }

    @Test
    void adminContentByIdRoute_matchesOnlyThatExactShape_notTheCollectionPathOrAnUnrelatedAdminPath() {
        // Not /api/admin/content/** — a deeper sub-path must not collide,
        // and this must not swallow the collection-level admin-content
        // route or any other existing admin route.
        assertThat(matchFor("/api/admin/content/123e4567-e89b-12d3-a456-426614174000/extra")).isEmpty();
        assertThat(matchFor("/api/admin/content")).isNotEmpty();
        assertThat(matchFor("/api/admin/content").get().getId()).isEqualTo("admin-content");
        assertThat(matchFor("/api/admin/bookings")).isNotEmpty();
        assertThat(matchFor("/api/admin/bookings").get().getId()).isEqualTo("admin-bookings");
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
    }

    @Test
    void adminShowCancelPath_goesToCatalogService() {
        // Phase 13.6.3: a second explicit admin route, same discipline as
        // admin-content — one endpoint, one route, to its real owning service.
        assertRoutedTo("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel",
                "admin-show-cancel", "http://localhost:8082");
    }

    @Test
    void adminShowCancelRoute_matchesOnlyThatExactShape_notAGenericAdminShowsPath() {
        // Not /api/admin/shows/** — /api/admin/shows and a bare
        // /api/admin/shows/{id} are now real routes of their own
        // (admin-shows, admin-show-by-id, FR-38) rather than unrouted, so
        // this only asserts neither is *this* (cancel) route.
        assertThat(matchFor("/api/admin/shows")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows").get().getId()).isEqualTo("admin-shows");
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-show-by-id");
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel/extra")).isEmpty();
        // And it must not overlap with the other admin route either.
        assertThat(matchFor("/api/admin/content")).isNotEmpty();
        assertThat(matchFor("/api/admin/content").get().getId()).isEqualTo("admin-content");
    }

    @Test
    void adminShowsPath_goesToCatalogService() {
        // FR-38: POST /api/admin/shows — the collection-level route,
        // separate from admin-show-cancel (different path shape entirely).
        assertRoutedTo("/api/admin/shows", "admin-shows", "http://localhost:8082");
    }

    @Test
    void adminShowsRoute_doesNotCollideWithAdminShowCancelOrAdminShowById() {
        // admin-show-cancel must keep working, and a bare
        // /api/admin/shows/{id} is now a real route of its own
        // (admin-show-by-id, FR-38) rather than unrouted — neither is
        // *this* (collection) route.
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-show-by-id");
    }

    @Test
    void adminShowByIdPath_goesToCatalogService() {
        // FR-38: PUT and DELETE /api/admin/shows/{id} both resolve here —
        // Spring Cloud Gateway Path predicates are not HTTP-method-specific
        // (no Method= predicate on this or any route in this project), so
        // one route already serves both verbs; no second route was added
        // for DELETE. assertRoutedTo/matchFor probe the route table only
        // by path (see their own implementation), which is exactly why a
        // single check here is representative of every verb against this
        // path shape.
        assertRoutedTo("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000",
                "admin-show-by-id", "http://localhost:8082");
    }

    @Test
    void everyAdminShowOperation_resolvesToItsOwnCorrectRoute() {
        // FR-38: consolidated proof that POST (create), PUT/DELETE
        // (admin-show-by-id, same path shape) and PATCH .../cancel each
        // still resolve to the correct, distinct route now that all four
        // coexist — no route was added for DELETE specifically, since
        // admin-show-by-id already covers it.
        assertThat(matchFor("/api/admin/shows")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows").get().getId()).isEqualTo("admin-shows");

        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-show-by-id");

        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
    }

    @Test
    void adminShowByIdRoute_doesNotStealTheCancelRoute_orTheCollectionRoute() {
        // A longer path (.../cancel) must still resolve to admin-show-cancel,
        // not admin-show-by-id, and the bare collection path must still
        // resolve to admin-shows, not admin-show-by-id.
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
        assertThat(matchFor("/api/admin/shows")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows").get().getId()).isEqualTo("admin-shows");
        // And no deeper sub-path collides either.
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/extra")).isEmpty();
    }

    @Test
    void adminVenuesPath_goesToCatalogService() {
        // FR-38: the first admin Venue route, same discipline as
        // admin-content/admin-shows — the collection-level path only.
        assertRoutedTo("/api/admin/venues", "admin-venues", "http://localhost:8082");
    }

    @Test
    void adminVenuesRoute_matchesOnlyThatExactPath_notASubPathOrAnUnrelatedAdminPath() {
        // Not /api/admin/venues/** — a bare by-id venue path is now a real
        // route of its own (admin-venue-by-id, FR-38) rather than unrouted,
        // and this must not overlap with any other existing admin route.
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-venue-by-id");
        assertThat(matchFor("/api/admin/venues/extra")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues/extra").get().getId()).isEqualTo("admin-venue-by-id");
        assertThat(matchFor("/api/admin/content")).isNotEmpty();
        assertThat(matchFor("/api/admin/content").get().getId()).isEqualTo("admin-content");
        assertThat(matchFor("/api/admin/shows")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows").get().getId()).isEqualTo("admin-shows");
        assertThat(matchFor("/api/admin/bookings")).isNotEmpty();
        assertThat(matchFor("/api/admin/bookings").get().getId()).isEqualTo("admin-bookings");
    }

    @Test
    void adminVenueByIdPath_goesToCatalogService() {
        // FR-38: PUT and DELETE /api/admin/venues/{id} both resolve here —
        // Spring Cloud Gateway Path predicates are not HTTP-method-specific
        // (no Method= predicate on this or any route in this project), so
        // one route already serves both verbs; no second route was added
        // for DELETE, mirroring admin-content-by-id/admin-show-by-id.
        assertRoutedTo("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000",
                "admin-venue-by-id", "http://localhost:8082");
    }

    @Test
    void everyAdminVenueOperation_resolvesToItsOwnCorrectRoute() {
        // FR-38: consolidated proof that POST (create) and PUT/DELETE
        // (admin-venue-by-id, same path shape) both still resolve to the
        // correct, distinct route — no route was added for DELETE
        // specifically, since admin-venue-by-id already covers it.
        assertThat(matchFor("/api/admin/venues")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues").get().getId()).isEqualTo("admin-venues");

        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-venue-by-id");
    }

    @Test
    void adminVenueByIdRoute_doesNotStealTheCollectionRoute() {
        // The bare collection path must still resolve to admin-venues, not
        // admin-venue-by-id, and vice versa — the two stay distinct.
        assertThat(matchFor("/api/admin/venues")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues").get().getId()).isEqualTo("admin-venues");
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-venue-by-id");
        // No deeper sub-path collides either.
        assertThat(matchFor("/api/admin/venues/123e4567-e89b-12d3-a456-426614174000/extra")).isEmpty();
    }

    @Test
    void adminBookingsPath_goesToBookingService() {
        // Phase 13.7.1: a third explicit admin route, same discipline as
        // admin-content/admin-show-cancel.
        assertRoutedTo("/api/admin/bookings", "admin-bookings", "http://localhost:8083");
    }

    @Test
    void adminBookingsRoute_matchesOnlyThatExactPath_notAnUnrelatedAdminPath() {
        // Not /api/admin/bookings/** — a future per-booking admin path (if
        // ever added) must not silently route here, and this must not
        // overlap with either existing admin route.
        assertThat(matchFor("/api/admin/bookings/123e4567-e89b-12d3-a456-426614174000")).isEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
        assertThat(matchFor("/api/admin/content")).isNotEmpty();
        assertThat(matchFor("/api/admin/content").get().getId()).isEqualTo("admin-content");
    }

    @Test
    void adminShowSeatActivityPath_goesToBookingService() {
        // Phase 13.7.2: a fourth explicit admin route, same discipline as
        // the three above.
        assertRoutedTo("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/seat-activity",
                "admin-show-seat-activity", "http://localhost:8083");
    }

    @Test
    void adminShowSeatActivityRoute_matchesOnlyThatExactShape_notAnUnrelatedAdminPath() {
        // Not /api/admin/shows/** — a bare show id is now a real route of
        // its own (admin-show-by-id, FR-38) rather than unrouted, and the
        // sibling admin-show-cancel path (same prefix, different final
        // segment) must not collide with this route either.
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000").get().getId())
                .isEqualTo("admin-show-by-id");
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/seat-activity/extra")).isEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel")).isNotEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel").get().getId())
                .isEqualTo("admin-show-cancel");
        assertThat(matchFor("/api/admin/bookings")).isNotEmpty();
        assertThat(matchFor("/api/admin/bookings").get().getId()).isEqualTo("admin-bookings");
    }

    @Test
    void adminUsersPath_goesToUserService() {
        // FR-37 fix: the pre-existing gap — GET /api/admin/users (Phase
        // 13.4) had no route at all — closed with the same
        // explicit-per-endpoint discipline as the other admin routes.
        assertRoutedTo("/api/admin/users", "admin-users", "http://localhost:8081");
    }

    @Test
    void adminUsersPlanAndRolePaths_goToUserService() {
        assertRoutedTo("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/plan",
                "admin-users-plan", "http://localhost:8081");
        assertRoutedTo("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/role",
                "admin-users-role", "http://localhost:8081");
    }

    @Test
    void adminUsersRoutes_matchOnlyTheirExactShape_notEachOtherOrAnUnrelatedAdminPath() {
        // Not /api/admin/users/** — a bare user id, and a plan path, must
        // not collide with the role path or vice versa.
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000")).isEmpty();
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/plan/extra")).isEmpty();
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/role/extra")).isEmpty();
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/plan")).isNotEmpty();
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/plan").get().getId())
                .isEqualTo("admin-users-plan");
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/role")).isNotEmpty();
        assertThat(matchFor("/api/admin/users/123e4567-e89b-12d3-a456-426614174000/role").get().getId())
                .isEqualTo("admin-users-role");
        // And it must not overlap with the unrelated existing admin routes either.
        assertThat(matchFor("/api/admin/bookings")).isNotEmpty();
        assertThat(matchFor("/api/admin/bookings").get().getId()).isEqualTo("admin-bookings");
        assertThat(matchFor("/api/admin/content")).isNotEmpty();
        assertThat(matchFor("/api/admin/content").get().getId()).isEqualTo("admin-content");
    }

    @Test
    void unroutedPaths_matchNothing() {
        assertThat(matchFor("/api/unknown")).isEmpty();
        assertThat(matchFor("/actuator/env")).isEmpty();
    }

    @Test
    void routesForwardThePathUnchanged() {
        // Backends already serve /api/... themselves; a StripPrefix or
        // RewritePath filter here would break that. The only filter is the
        // CORS-header dedupe default filter (Phase 7.2), which touches
        // response headers, never the request path.
        assertThat(routes()).allSatisfy(route -> {
            assertThat(route.getFilters()).hasSize(1);
            assertThat(route.getFilters().get(0).toString())
                    .contains("DedupeResponseHeader")
                    .doesNotContain("StripPrefix")
                    .doesNotContain("RewritePath");
        });
    }
}
