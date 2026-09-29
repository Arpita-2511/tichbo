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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking confirmation is payment-service's internal job. The old public
 * {@code POST /api/bookings/{id}/confirm} let any signed-in customer confirm
 * an unpaid booking (found in Phase 15 Step 4), so the Gateway denies that
 * path for every role — admin included, per FR-39 — and never forwards it.
 * {@code /internal/**} has no route at all. The remaining booking routes are
 * unaffected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayBookingConfirmDeniedTest {

    private static final StubUpstream STUB = new StubUpstream();
    private static final String ID = "8a2c1e3d-0000-0000-0000-000000000000";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
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

    @Test
    void customerConfirm_is403_andNeverForwarded() {
        send(HttpMethod.POST, "/api/bookings/" + ID + "/confirm", customer())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("FORBIDDEN");

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void adminConfirm_is403_andNeverForwarded() {
        send(HttpMethod.POST, "/api/bookings/" + ID + "/confirm", admin()).expectStatus().isForbidden();

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void unauthenticatedConfirm_is401_andNeverForwarded() {
        send(HttpMethod.POST, "/api/bookings/" + ID + "/confirm", null).expectStatus().isUnauthorized();
        send(HttpMethod.POST, "/api/bookings/" + ID + "/confirm", "Bearer not.a.jwt").expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void confirmPath_isDeniedForEveryMethod() {
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            send(method, "/api/bookings/" + ID + "/confirm", admin()).expectStatus().isForbidden();
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void internalConfirmAndCancel_haveNoGatewayRoute() {
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/confirm", admin()).expectStatus().isNotFound();
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/confirm", customer()).expectStatus().isNotFound();
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/cancel", admin()).expectStatus().isNotFound();
        send(HttpMethod.POST, "/internal/bookings/" + ID + "/confirm", null).expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void theRemainingBookingRoutes_stillForward() {
        send(HttpMethod.POST, "/api/bookings", customer()).expectStatus().isOk();
        send(HttpMethod.GET, "/api/bookings", customer()).expectStatus().isOk();
        send(HttpMethod.GET, "/api/bookings/" + ID, customer()).expectStatus().isOk();
        send(HttpMethod.POST, "/api/bookings/" + ID + "/cancel", customer()).expectStatus().isOk();
        send(HttpMethod.GET, "/api/bookings/shows/" + ID + "/seats", customer()).expectStatus().isOk();

        assertThat(STUB.received()).hasSize(5);
    }
}
