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
 * Phase 15 Step 2: the new {@code payment-service} route
 * ({@code Path=/api/payments/** -> localhost:8084}) actually forwards a
 * request, alongside the three existing top-level routes, and existing
 * JWT authentication rules apply to it with no new Gateway security code
 * (the same {@code anyExchange().authenticated()} catch-all covers it).
 *
 * <p>See also {@code GatewayRoutesTest} (route table checked structurally,
 * without sending traffic) and {@code GatewayAdminContentRouteTest} (the
 * precedent this class's shape follows exactly, for the equivalent
 * {@code admin-content} route).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayPaymentRouteTest {

    private static final StubUpstream STUB = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // A list property from a higher-priority source replaces
        // application.yml's whole routes list, not just the indices it
        // sets (see StubUpstream.routeEverythingTo's own comment) — so the
        // new 4th route is restated here too, alongside the other three,
        // rather than silently disappearing for this test class.
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.cloud.gateway.routes[3].id", () -> "payment-service");
        registry.add("spring.cloud.gateway.routes[3].uri", STUB::url);
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/api/payments/**");
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

    private static String customerToken() {
        return "Bearer " + TestTokens.valid().role("CUSTOMER").build();
    }

    @Test
    void unauthenticatedPaymentRequest_is401_andNeverReachesTheUpstream() {
        client.post().uri("/api/payments")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void authenticatedPaymentRequest_reachesThePaymentServiceUpstream_throughTheDedicatedRoute() {
        client.post().uri("/api/payments")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).method()).isEqualTo("POST");
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/payments");
    }

    @Test
    void authenticatedPaymentLookup_reachesThePaymentServiceUpstream() {
        client.get().uri("/api/payments/8a2c1e3d-0000-0000-0000-000000000000")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/payments/8a2c1e3d-0000-0000-0000-000000000000");
    }

    @Test
    void theOtherThreeRoutes_stillWorkAlongsideTheNewPaymentRoute() {
        client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/catalog/content");
    }
}
