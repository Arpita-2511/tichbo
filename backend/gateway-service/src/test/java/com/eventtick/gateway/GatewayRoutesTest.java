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
 * Phase 7.1 (plus the Phase 13.5.3/13.6.3 admin routes): every route exists, each
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
    void exactlyFiveRoutesAreRegistered() {
        assertThat(routes()).extracting(Route::getId)
                .containsExactlyInAnyOrder("user-service", "catalog-service", "booking-service",
                        "admin-content", "admin-show-cancel");
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
        // not a catch-all — a sub-path or a different future admin
        // endpoint must not silently start routing through it.
        assertThat(matchFor("/api/admin/content/extra")).isEmpty();
        assertThat(matchFor("/api/admin/users")).isEmpty();
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
        // Not /api/admin/shows/** — a plain "list" or "get by id" admin
        // shows path (if one is ever added) must not silently route here.
        assertThat(matchFor("/api/admin/shows")).isEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000")).isEmpty();
        assertThat(matchFor("/api/admin/shows/123e4567-e89b-12d3-a456-426614174000/cancel/extra")).isEmpty();
        // And it must not overlap with the other admin route either.
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
