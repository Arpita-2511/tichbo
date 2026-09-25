package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-40: {@code GET /api/admin/rate-limits/stats}, end to end — a real
 * gateway, real (embedded, see {@link TestRedis}) Redis, and real traffic
 * through {@link RateLimitingGlobalFilter}, the same style
 * {@code GatewayRateLimitTest} already uses.
 *
 * <p>The {@code AUTH.PUBLIC} policy is overridden to small, distinctive
 * numbers here specifically so the response can be checked against those
 * <i>overridden</i> values rather than the shipped defaults — proving the
 * controller reads {@code RateLimitPolicyProperties} live rather than
 * returning something hardcoded (a hardcoded response would still show the
 * shipped {@code 2/5/1}, not these).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRateLimitStatsTest {

    private static final StubUpstream STUB = new StubUpstream();
    private static final TestRedis REDIS = TestRedis.start();

    // RedisRateLimiter.Config requires burstCapacity >= replenishRate.
    private static final int AUTH_PUBLIC_REPLENISH = 1;
    private static final int AUTH_PUBLIC_BURST = 2;
    private static final int FALLBACK_REPLENISH = 77;
    private static final int FALLBACK_BURST = 88;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);

        registry.add("eventtick.rate-limit.policies.AUTH.PUBLIC.replenish-rate", () -> AUTH_PUBLIC_REPLENISH);
        registry.add("eventtick.rate-limit.policies.AUTH.PUBLIC.burst-capacity", () -> AUTH_PUBLIC_BURST);
        // requested-tokens == burst-capacity: one call drains the whole
        // bucket, so the next call is deterministically rejected regardless
        // of timing — the same technique GatewayRateLimitTest uses (a
        // per-call cost of 1 against replenish-rate >= 1/sec was observed to
        // flake if a request happened to land >1s after the previous one).
        registry.add("eventtick.rate-limit.policies.AUTH.PUBLIC.requested-tokens", () -> AUTH_PUBLIC_BURST);
        registry.add("eventtick.rate-limit.fallback.replenish-rate", () -> FALLBACK_REPLENISH);
        registry.add("eventtick.rate-limit.fallback.burst-capacity", () -> FALLBACK_BURST);
        registry.add("eventtick.rate-limit.fallback.requested-tokens", () -> 1);
    }

    @AfterAll
    static void stopInfrastructure() {
        STUB.stop();
        REDIS.stop();
    }

    @Autowired
    private WebTestClient client;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void freshState() {
        STUB.clear();
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
    }

    private static String adminToken() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    private static String customerToken() {
        return "Bearer " + TestTokens.valid().role("CUSTOMER").build();
    }

    private void awaitRecording() {
        // recordAllowed/recordRejected are fire-and-forget (see
        // RateLimitActivityRecorder) — give them a moment to land before
        // reading stats back, the same race a real dashboard read would have.
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- A: policy matrix, no hardcoded values ----

    @Test
    void policyMatrix_reflectsTheConfiguredValues_notHardcoded() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policies.AUTH.PUBLIC.replenishRate").isEqualTo(AUTH_PUBLIC_REPLENISH)
                .jsonPath("$.policies.AUTH.PUBLIC.burstCapacity").isEqualTo(AUTH_PUBLIC_BURST)
                .jsonPath("$.policies.AUTH.PUBLIC.requestedTokens").isEqualTo(AUTH_PUBLIC_BURST)
                // CATALOG/BOOKING/USER are untouched by this test's overrides —
                // still present, proving the whole matrix is echoed, not just AUTH.
                .jsonPath("$.policies.CATALOG.FREE.replenishRate").exists()
                .jsonPath("$.policies.BOOKING.FREE.replenishRate").exists()
                .jsonPath("$.policies.USER.FREE.replenishRate").exists();
    }

    @Test
    void fallbackPolicy_matchesConfiguration() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fallback.replenishRate").isEqualTo(FALLBACK_REPLENISH)
                .jsonPath("$.fallback.burstCapacity").isEqualTo(FALLBACK_BURST)
                .jsonPath("$.fallback.requestedTokens").isEqualTo(1);
    }

    // ---- C: activity is returned correctly; multiple policy IDs represented ----

    @Test
    void activity_reflectsRealTraffic_forThePolicyThatActuallyHandledIt() {
        // requested-tokens == burst-capacity: the first login drains the
        // whole bucket in one call, so the second is deterministically
        // rejected regardless of timing — real AUTH:PUBLIC allowed=1, rejected=1.
        client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}")
                .exchange().expectStatus().isOk();
        client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}")
                .exchange().expectStatus().isEqualTo(429);
        awaitRecording();

        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activity.redisAvailable").isEqualTo(true)
                .jsonPath("$.activity.byPolicy['AUTH:PUBLIC'].allowed").isEqualTo(1)
                .jsonPath("$.activity.byPolicy['AUTH:PUBLIC'].rejected").isEqualTo(1)
                // A policy nothing hit yet is still represented, at zero.
                .jsonPath("$.activity.byPolicy['CATALOG:FREE'].allowed").isEqualTo(0)
                .jsonPath("$.activity.byPolicy['CATALOG:FREE'].rejected").isEqualTo(0)
                .jsonPath("$.activity.byPolicy['FALLBACK']").exists();
    }

    // ---- E: authorization ----

    @Test
    void adminJwt_reachesTheEndpoint_andReturns200() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void customerJwt_isForbidden_403() {
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void noJwt_isUnauthorized_401() {
        client.get().uri("/api/admin/rate-limits/stats")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void thisEndpoint_isNeverForwardedToAnUpstream() {
        // It is served locally — no proxying, regardless of outcome.
        client.get().uri("/api/admin/rate-limits/stats")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).isEmpty();
    }
}
