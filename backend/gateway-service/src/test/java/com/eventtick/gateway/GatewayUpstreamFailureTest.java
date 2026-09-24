package com.eventtick.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.4: what a client sees when the gateway is fine but the service
 * behind it is down. All routes point at a port that was free a moment ago and
 * has nothing listening, so the connection is genuinely refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayUpstreamFailureTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final int DEAD_PORT = unusedPort();

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, "http://127.0.0.1:" + DEAD_PORT);
    }

    @Autowired
    private WebTestClient client;

    private void assertControlled503(EntityExchangeResult<byte[]> result, String expectedId) {
        assertThat(result.getStatus().value()).isEqualTo(503);
        assertThat(result.getResponseHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        assertThat(result.getResponseHeaders().get("X-Request-ID")).containsExactly(expectedId);

        String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":503")
                .contains("\"error\":\"SERVICE_UNAVAILABLE\"")
                .contains("\"message\":\"The requested service is temporarily unavailable.\"")
                .contains("\"requestId\":\"" + expectedId + "\"")
                // The refused-connection exception names the internal host and port; none of it may leak.
                .doesNotContain("Exception")
                .doesNotContain("refused")
                .doesNotContain("127.0.0.1")
                .doesNotContain("localhost")
                .doesNotContain(String.valueOf(DEAD_PORT))
                .doesNotContain("io.netty")
                .doesNotContain("java.")
                .doesNotContain("at com.");
    }

    @Test
    void refusedUpstreamConnection_isAControlled503_withoutInternalDetails() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .header("X-Request-ID", "id-for-a-503")
                .exchange()
                .expectBody().returnResult();

        assertControlled503(result, "id-for-a-503");
    }

    @Test
    void publicLoginWithAnUnreachableUserService_isAlsoControlled() {
        EntityExchangeResult<byte[]> result = client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("X-Request-ID", "id-for-login-503")
                .bodyValue("{}")
                .exchange()
                .expectBody().returnResult();

        assertControlled503(result, "id-for-login-503");
    }

    @Test
    void theControlled503_keepsItsCorsHeaders_soTheBrowserCanReadIt() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .header("Origin", ALLOWED_ORIGIN)
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(503);
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(result.getResponseHeaders().get("X-Request-ID")).hasSize(1);
    }
}
