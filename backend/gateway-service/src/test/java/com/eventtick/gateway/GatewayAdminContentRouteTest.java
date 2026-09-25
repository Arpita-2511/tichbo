package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13.5.3: the dedicated {@code admin-content} route
 * ({@code Path=/api/admin/content -> catalog-service}) actually forwards a
 * request, alongside the other three routes.
 *
 * <p>Deliberately does <b>not</b> re-test {@code CUSTOMER -> 403} or
 * missing/invalid JWT {@code -> 401} for this path — the
 * {@code /api/admin/** -> ROLE_ADMIN} rule is a single path-pattern match
 * evaluated before any route is chosen, already exhaustively proven generic
 * (path-independent) by {@code GatewayAdminAuthorizationTest}. This class
 * only proves the *routing* half: that an authorized request for this exact
 * path reaches catalog-service, and that adding this 4th route didn't
 * disturb the other three (see also {@code GatewayRoutesTest}, which checks
 * the route table structurally, without sending traffic).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAdminContentRouteTest {

    private static final StubUpstream STUB = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // A list property from a higher-priority source replaces
        // application.yml's whole routes list, not just the indices it
        // sets (see StubUpstream.routeEverythingTo's own comment) — so the
        // new 4th route is restated here too, alongside the other three,
        // rather than silently disappearing for this test class.
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

    private static String adminToken() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    @Test
    void adminJwt_postToApiAdminContent_reachesTheCatalogUpstream_throughTheDedicatedRoute() {
        client.post().uri("/api/admin/content")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).method()).isEqualTo("POST");
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/admin/content");
    }

    @Test
    void theOtherThreeRoutes_stillWorkAlongsideTheNewAdminRoute() {
        client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/catalog/content");
    }
}
