package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * FR-40: {@code GET /api/admin/rate-limits/stats} when Redis is
 * unreachable — the same "real refused connection" technique
 * {@code GatewayRateLimitRedisUnavailableTest} already uses. The endpoint
 * must never 5xx: the policy matrix (Redis-independent) is still returned,
 * and {@code activity.redisAvailable} reports the degraded state instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRateLimitStatsRedisUnavailableTest {

    private static final StubUpstream STUB = new StubUpstream();
    private static final int DEAD_REDIS_PORT = unusedPort();

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> DEAD_REDIS_PORT);
    }

    @AfterAll
    static void stopInfrastructure() {
        STUB.stop();
    }

    @Autowired
    private WebTestClient client;

    private static String adminToken() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    @Test
    void statsEndpoint_returns200_notA5xx_whenRedisIsUnreachable() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void activity_reportsRedisUnavailable_withAnEmptyByPolicyMap() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activity.redisAvailable").isEqualTo(false)
                .jsonPath("$.activity.byPolicy").isEmpty();
    }

    @Test
    void policyMatrix_isStillReturned_evenThoughRedisIsDown() {
        // The matrix comes from RateLimitPolicyProperties (in-memory config),
        // not Redis — it must be unaffected by the outage.
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policies.AUTH.PUBLIC.replenishRate").exists()
                .jsonPath("$.fallback.replenishRate").exists();
    }

    @Test
    void ordinaryTraffic_stillSucceeds_rateLimiterFailsOpen_unaffectedByTheStatsEndpoint() {
        // The existing fail-open behavior (GatewayRateLimitRedisUnavailableTest)
        // must be completely unaffected by this endpoint's existence.
        client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                .exchange()
                .expectStatus().isOk();
    }
}
