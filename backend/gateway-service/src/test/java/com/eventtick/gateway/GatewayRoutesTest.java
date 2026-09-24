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
 * Phase 7.1: the three routes exist, each request path lands on the right
 * upstream, and nothing rewrites the path (no filters on any route).
 * Does not start the upstream services or send real traffic — it asks the
 * gateway's own route table which route a given path would match.
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
    void exactlyThreeRoutesAreRegistered() {
        assertThat(routes()).extracting(Route::getId)
                .containsExactlyInAnyOrder("user-service", "catalog-service", "booking-service");
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
    void unroutedPaths_matchNothing() {
        assertThat(matchFor("/api/unknown")).isEmpty();
        assertThat(matchFor("/actuator/env")).isEmpty();
    }

    @Test
    void routesForwardThePathUnchanged() {
        // Backends already serve /api/... themselves; any filter here
        // (StripPrefix, RewritePath, ...) would break that.
        assertThat(routes()).allSatisfy(route -> assertThat(route.getFilters()).isEmpty());
    }
}
