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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13.6.3: the dedicated {@code admin-show-cancel} route
 * ({@code Path=/api/admin/shows/{id}/cancel -> catalog-service}) actually
 * forwards a request, alongside the other four routes. Mirrors
 * {@link GatewayAdminContentRouteTest} (Phase 13.5.3).
 *
 * <p>Deliberately does <b>not</b> re-test {@code CUSTOMER -> 403} or
 * missing/invalid JWT {@code -> 401} for this path — already exhaustively
 * proven path-independent by {@code GatewayAdminAuthorizationTest}. This
 * class only proves the *routing* half.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAdminShowCancelRouteTest {

    private static final StubUpstream STUB = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Every route is restated here — a list property from a
        // higher-priority source replaces application.yml's whole routes
        // list, not just the indices it sets (see
        // StubUpstream.routeEverythingTo's own comment).
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.cloud.gateway.routes[3].id", () -> "admin-content");
        registry.add("spring.cloud.gateway.routes[3].uri", STUB::url);
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/api/admin/content");
        registry.add("spring.cloud.gateway.routes[4].id", () -> "admin-show-cancel");
        registry.add("spring.cloud.gateway.routes[4].uri", STUB::url);
        registry.add("spring.cloud.gateway.routes[4].predicates[0]", () -> "Path=/api/admin/shows/{id}/cancel");
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
    void adminJwt_patchToApiAdminShowsCancel_reachesTheCatalogUpstream_throughTheDedicatedRoute() {
        UUID showId = UUID.randomUUID();

        client.method(HttpMethod.PATCH)
                .uri("/api/admin/shows/" + showId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).method()).isEqualTo("PATCH");
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/admin/shows/" + showId + "/cancel");
    }

    @Test
    void theOtherFourRoutes_stillWorkAlongsideTheNewAdminShowCancelRoute() {
        client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();
        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/catalog/content");

        STUB.clear();
        client.post().uri("/api/admin/content")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/admin/content");
    }
}
