package com.eventtick.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.5: the production {@code application.yml} really does bind the
 * connection- and response-timeout the gateway is meant to enforce, and the
 * three existing routes are untouched by adding them. Uses the real
 * configuration (no {@code @DynamicPropertySource} override), so this fails
 * if someone removes or weakens the timeout config.
 */
@SpringBootTest
class GatewayTimeoutConfigTest {

    // Mirrors src/main/resources/application.yml's defaults (no JWT_SECRET/etc.
    // needed here since this context isn't invoking authentication or routing).
    private static final int EXPECTED_CONNECT_TIMEOUT_MS = 3000;
    private static final Duration EXPECTED_RESPONSE_TIMEOUT = Duration.ofSeconds(8);

    @Autowired
    private HttpClientProperties httpClientProperties;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void connectTimeout_isConfigured_toAConservativeDevValue() {
        assertThat(httpClientProperties.getConnectTimeout()).isEqualTo(EXPECTED_CONNECT_TIMEOUT_MS);
    }

    @Test
    void responseTimeout_isConfigured_toAConservativeDevValue() {
        assertThat(httpClientProperties.getResponseTimeout()).isEqualTo(EXPECTED_RESPONSE_TIMEOUT);
    }

    @Test
    void bothTimeouts_areASingleSharedHttpClientSetting_soEveryRouteInheritsThem() {
        // Spring Cloud Gateway proxies every route through the one HttpClient
        // built from these properties; none of the routes below declare a
        // per-route override, so this configuration alone covers all of them.
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId)
                .containsExactlyInAnyOrder("user-service", "catalog-service", "booking-service",
                        "admin-content", "admin-show-cancel");
        assertThat(routes).allSatisfy(route ->
                assertThat(route.getMetadata()).as("route %s has no per-route timeout override", route.getId())
                        .doesNotContainKeys("connect-timeout", "response-timeout"));
    }

    @Test
    void timeoutsAreConservative_notAggressiveProductionValues() {
        // Guards against someone "tightening" these into the kind of
        // sub-second values that would make normal local dev traffic flaky.
        assertThat(httpClientProperties.getConnectTimeout()).isGreaterThanOrEqualTo(1000);
        assertThat(httpClientProperties.getResponseTimeout()).isGreaterThanOrEqualTo(Duration.ofSeconds(3));
    }
}
