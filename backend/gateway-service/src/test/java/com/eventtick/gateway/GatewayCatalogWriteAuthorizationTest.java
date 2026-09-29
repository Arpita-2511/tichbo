package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog management is admin-only at the Gateway (Phase 15 Step 4
 * follow-up). Found by the Step 4 integration test: a plain CUSTOMER could
 * create content and venues and delete a venue through
 * {@code /api/catalog/**}. Reads must stay open to any authenticated user;
 * writes on content/venues/seats/shows need {@code ROLE_ADMIN}; the explicit
 * {@code /api/admin/**} routes and unauthenticated behaviour must be
 * unchanged; and {@code /internal/**} (booking-service's service-to-service
 * surface) must not be routable at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCatalogWriteAuthorizationTest {

    private static final StubUpstream STUB = new StubUpstream();
    private static final List<String> MANAGED = List.of("content", "venues", "seats", "shows");
    private static final String ID = "8a2c1e3d-0000-0000-0000-000000000000";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // See GatewayPaymentRouteTest: a list property replaces the whole
        // routes list, so the extra admin route is restated after the base three.
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.cloud.gateway.routes[3].id", () -> "admin-content");
        registry.add("spring.cloud.gateway.routes[3].uri", STUB::url);
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/api/admin/content");
    }

    @AfterAll
    static void stopInfrastructure() {
        STUB.stop();
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void clearRecorded() {
        STUB.clear();
    }

    private static String customer() {
        return "Bearer " + TestTokens.valid().role("CUSTOMER").build();
    }

    private static String admin() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    private WebTestClient.ResponseSpec send(HttpMethod method, String path, String authorization) {
        WebTestClient.RequestBodySpec request = client.method(method).uri(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (authorization != null) {
            request = (WebTestClient.RequestBodySpec) request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.bodyValue("{}").exchange();
    }

    // ---- reads stay open to a customer ----

    @Test
    void customer_canReadContentVenuesShowsAndSeats() {
        for (String resource : MANAGED) {
            send(HttpMethod.GET, "/api/catalog/" + resource, customer()).expectStatus().isOk();
            send(HttpMethod.GET, "/api/catalog/" + resource + "/" + ID, customer()).expectStatus().isOk();
        }
        assertThat(STUB.received()).hasSize(MANAGED.size() * 2);
    }

    // ---- writes are refused for a customer, and never forwarded ----

    @Test
    void customer_cannotCreateUpdatePatchOrDelete_anyManagedCatalogResource() {
        for (String resource : MANAGED) {
            send(HttpMethod.POST, "/api/catalog/" + resource, customer()).expectStatus().isForbidden();
            send(HttpMethod.PUT, "/api/catalog/" + resource + "/" + ID, customer()).expectStatus().isForbidden();
            send(HttpMethod.PATCH, "/api/catalog/" + resource + "/" + ID, customer()).expectStatus().isForbidden();
            send(HttpMethod.DELETE, "/api/catalog/" + resource + "/" + ID, customer()).expectStatus().isForbidden();
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void customer_contentWrites_return403() {
        send(HttpMethod.POST, "/api/catalog/content", customer()).expectStatus().isForbidden();
        send(HttpMethod.PUT, "/api/catalog/content/" + ID, customer()).expectStatus().isForbidden();
        send(HttpMethod.DELETE, "/api/catalog/content/" + ID, customer()).expectStatus().isForbidden();
    }

    @Test
    void customer_venueWrites_return403() {
        send(HttpMethod.POST, "/api/catalog/venues", customer()).expectStatus().isForbidden();
        send(HttpMethod.PUT, "/api/catalog/venues/" + ID, customer()).expectStatus().isForbidden();
        send(HttpMethod.DELETE, "/api/catalog/venues/" + ID, customer()).expectStatus().isForbidden();
    }

    @Test
    void customer_showManagementThroughCatalog_returns403() {
        send(HttpMethod.POST, "/api/catalog/shows", customer()).expectStatus().isForbidden();
        send(HttpMethod.PUT, "/api/catalog/shows/" + ID, customer()).expectStatus().isForbidden();
        send(HttpMethod.DELETE, "/api/catalog/shows/" + ID, customer()).expectStatus().isForbidden();
    }

    @Test
    void forbiddenResponse_isTheGatewaysJsonForbidden_notA401() {
        send(HttpMethod.POST, "/api/catalog/venues", customer())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    // ---- admin keeps every existing catalog operation ----

    @Test
    void admin_catalogWrites_stillReachCatalogService() {
        for (String resource : MANAGED) {
            send(HttpMethod.POST, "/api/catalog/" + resource, admin()).expectStatus().isOk();
            send(HttpMethod.PUT, "/api/catalog/" + resource + "/" + ID, admin()).expectStatus().isOk();
            send(HttpMethod.DELETE, "/api/catalog/" + resource + "/" + ID, admin()).expectStatus().isOk();
        }
        assertThat(STUB.received()).hasSize(MANAGED.size() * 3);
    }

    // ---- unauthenticated behaviour is unchanged ----

    @Test
    void unauthenticated_isStill401_forReadsAndWrites() {
        send(HttpMethod.GET, "/api/catalog/content", null).expectStatus().isUnauthorized();
        send(HttpMethod.POST, "/api/catalog/content", null).expectStatus().isUnauthorized();
        send(HttpMethod.DELETE, "/api/catalog/venues/" + ID, null).expectStatus().isUnauthorized();
        assertThat(STUB.received()).isEmpty();
    }

    // ---- the explicit admin routes are unaffected ----

    @Test
    void adminRoutes_unaffected_adminReachesUpstream_customerStill403() {
        send(HttpMethod.POST, "/api/admin/content", admin()).expectStatus().isOk();
        send(HttpMethod.POST, "/api/admin/content", customer()).expectStatus().isForbidden();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/admin/content");
    }

    // ---- the rule is explicit, not a wildcard over unrelated services ----

    @Test
    void customerWrites_toBookingsAndUsersRoutes_areNotAffectedByTheCatalogRule() {
        send(HttpMethod.POST, "/api/bookings", customer()).expectStatus().isOk();
        send(HttpMethod.POST, "/api/bookings/" + ID + "/cancel", customer()).expectStatus().isOk();

        assertThat(STUB.received()).hasSize(2);
    }

    // ---- /internal/** is not reachable through the Gateway ----

    @Test
    void internalBookingPaths_haveNoGatewayRoute_evenForAnAdmin() {
        send(HttpMethod.GET, "/internal/bookings/" + ID, admin()).expectStatus().isNotFound();
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/confirm", admin()).expectStatus().isNotFound();
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/cancel", admin()).expectStatus().isNotFound();
        send(HttpMethod.GET, "/internal/bookings/" + ID, null).expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }
}
