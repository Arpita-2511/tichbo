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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11: what happens when Redis itself is unreachable. All routes point
 * at the working {@link StubUpstream}; only {@code spring.data.redis.*}
 * points at a port that was free a moment ago and now has nothing listening,
 * so the connection genuinely fails (the same "real refused connection"
 * pattern {@code GatewayUpstreamFailureTest} uses for an upstream).
 *
 * <p>{@link org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter}
 * fails open on a Redis error — this test exists to pin that down as
 * intended, documented behavior (a rate-limiter outage must not become a
 * full API outage) rather than an accident nobody verified.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRateLimitRedisUnavailableTest {

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
        // A small, tripped-over policy: if this were failing CLOSED instead of
        // open, even one request would show it.
        registry.add("spring.cloud.gateway.redis-rate-limiter.replenish-rate", () -> 1);
        registry.add("spring.cloud.gateway.redis-rate-limiter.burst-capacity", () -> 1);
    }

    @Autowired
    private WebTestClient client;

    @Test
    void requestsStillSucceed_whenRedisIsUnreachable_insteadOfBeingBlockedOrErroring() {
        // Several in a row, well past the burst capacity above — if the
        // limiter were failing closed (or erroring) this would show up fast.
        for (int i = 1; i <= 5; i++) {
            EntityExchangeResult<byte[]> result = client.get().uri("/api/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().build())
                    .exchange().expectBody().returnResult();

            assertThat(result.getStatus().value()).as("request %d", i).isEqualTo(200);
        }
        assertThat(STUB.received()).hasSize(5);
    }

    @Test
    void publicEndpoints_alsoStillWork_whenRedisIsUnreachable() {
        client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange().expectStatus().isOk();
    }
}
