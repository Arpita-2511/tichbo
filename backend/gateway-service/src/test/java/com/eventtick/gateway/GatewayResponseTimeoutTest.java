package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.5: an upstream that is up but too slow. The gateway's
 * {@code response-timeout} is shortened for this test class only (the
 * production default in {@code application.yml} is conservative and would
 * make a deterministic test slow); the stub then deliberately waits longer
 * than that before writing any response, on its own thread, so the timeout
 * is guaranteed to fire without depending on real network conditions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayResponseTimeoutTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    /** Comfortably shorter than the stub's delay below, and than a real network round trip. */
    private static final Duration TEST_RESPONSE_TIMEOUT = Duration.ofMillis(400);
    private static final Duration STUB_DELAY = Duration.ofSeconds(2);

    private static final StubUpstream SLOW_STUB = new StubUpstream(STUB_DELAY);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, SLOW_STUB.url());
        registry.add("spring.cloud.gateway.httpclient.response-timeout", () -> TEST_RESPONSE_TIMEOUT);
    }

    @AfterAll
    static void stopStub() {
        SLOW_STUB.stop();
    }

    @Autowired
    private WebTestClient client;

    @Test
    void aSlowUpstream_producesAControlled504_wellBeforeTheStubResponds() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .header("X-Request-ID", "id-for-a-504")
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(504);
        assertThat(result.getResponseHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        // Exactly one X-Request-ID, and it is the one supplied.
        assertThat(result.getResponseHeaders().get("X-Request-ID")).containsExactly("id-for-a-504");

        String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":504")
                .contains("\"error\":\"GATEWAY_TIMEOUT\"")
                .contains("\"message\":\"The requested service did not respond in time.\"")
                .contains("\"requestId\":\"id-for-a-504\"")
                .contains("\"timestamp\"")
                // No internal detail of the timeout may leak.
                .doesNotContain("Exception")
                .doesNotContain("Timeout")
                .doesNotContain("127.0.0.1")
                .doesNotContain("io.netty")
                .doesNotContain("reactor.netty")
                .doesNotContain("java.")
                .doesNotContain("at com.");
    }

    @Test
    void aSlowUpstream_withoutASuppliedId_stillGetsExactlyOneGeneratedId() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(504);
        assertThat(result.getResponseHeaders().get("X-Request-ID")).hasSize(1);
    }

    @Test
    void aSlowUpstream_publicLoginPath_alsoTimesOutCleanly() {
        // response-timeout applies before authentication is even relevant here —
        // login is public, and the upstream itself is what's slow.
        EntityExchangeResult<byte[]> result = client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(504);
    }

    @Test
    void the504_keepsItsCorsHeaders_soTheBrowserCanReadIt() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .header("Origin", ALLOWED_ORIGIN)
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(504);
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
    }
}
