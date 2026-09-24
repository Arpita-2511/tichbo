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
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11: the static Redis-backed rate limiter, end to end — a real
 * gateway, a real (embedded, see {@link TestRedis}) Redis, and the recording
 * {@link StubUpstream}. Each test starts from a freshly flushed Redis so
 * bucket state never leaks between tests regardless of execution order.
 *
 * <p>The test policy sets {@code requested-tokens == burst-capacity}, so a
 * <i>single</i> request drains a fresh bucket completely, rather than
 * exhausting it one token per call across several requests. That is
 * deliberate: the bucket's refill is measured against Redis's own wall
 * clock, and {@code replenish-rate} cannot go below 1 token/second (an
 * {@code int} in the underlying config), so a per-call cost of 1 token would
 * leave only a one-second margin between "the bucket is provably empty" and
 * "a stray token has refilled" — not reliably safe on a shared/loaded
 * machine (this was observed to flake under real load before this change).
 * Requiring the whole bucket to refill instead needs
 * {@code burst-capacity} seconds, a comfortable margin, without changing
 * what any test actually proves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRateLimitTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final int BURST_CAPACITY = 5;

    private static final StubUpstream STUB = new StubUpstream();
    private static final TestRedis REDIS = TestRedis.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);
        registry.add("spring.cloud.gateway.redis-rate-limiter.replenish-rate", () -> 1);
        registry.add("spring.cloud.gateway.redis-rate-limiter.burst-capacity", () -> BURST_CAPACITY);
        registry.add("spring.cloud.gateway.redis-rate-limiter.requested-tokens", () -> BURST_CAPACITY);
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
        // Warm the whole pipeline (routing, security, the rate limiter's
        // first EVAL of its Lua script, the upstream call) before any timed
        // assertion runs, so the first real request in a test isn't slower
        // than a not-yet-JIT'd path would otherwise make it.
        client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange();

        STUB.clear();
        // So no test's bucket usage (by IP, or by a reused token) can affect
        // another's — deterministic regardless of what ran before it or in
        // which order.
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
    }

    private String bearer(String subject) {
        return "Bearer " + TestTokens.valid().subject(subject).build();
    }

    private String freshUserToken() {
        // TestTokens mints a new random UUID subject each call, so each fresh
        // token is automatically its own bucket.
        return "Bearer " + TestTokens.valid().build();
    }

    private WebTestClient.RequestHeadersSpec<?> me(String authorization) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/users/me");
        return authorization == null ? request : request.header(HttpHeaders.AUTHORIZATION, authorization);
    }

    private WebTestClient.RequestHeadersSpec<?> publicLogin() {
        return client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}");
    }

    // ---- below the limit ----

    @Test
    void requestBelowTheLimit_succeeds_andCarriesRateLimitHeaders() {
        EntityExchangeResult<byte[]> result = me(freshUserToken()).exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        // The conventional headers RedisRateLimiter itself builds. A single
        // request costs the entire configured burst capacity (see class
        // Javadoc), so a fresh bucket has none left right after it.
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Burst-Capacity")).isEqualTo(String.valueOf(BURST_CAPACITY));
        assertThat(result.getResponseHeaders().getFirst("X-RateLimit-Replenish-Rate")).isEqualTo("1");
    }

    // ---- at/above the limit ----

    @Test
    void aSecondRequestFromTheSameIdentity_isRejectedWith429_withTheStandardBody() {
        String auth = freshUserToken();

        me(auth).exchange().expectStatus().isOk(); // first request drains the bucket

        EntityExchangeResult<byte[]> limited = me(auth).header("X-Request-ID", "id-for-a-429").exchange()
                .expectBody().returnResult();

        assertThat(limited.getStatus().value()).isEqualTo(429);
        assertThat(limited.getResponseHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        assertThat(limited.getResponseHeaders().get("X-Request-ID")).containsExactly("id-for-a-429");

        String body = new String(limited.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":429")
                .contains("\"error\":\"TOO_MANY_REQUESTS\"")
                .contains("\"requestId\":\"id-for-a-429\"")
                .contains("\"timestamp\"")
                .contains("\"message\"")
                // No Redis/implementation detail may leak into the body.
                .doesNotContain("redis").doesNotContain("Redis").doesNotContain("Lua")
                .doesNotContain("Exception").doesNotContain("localhost:" + REDIS.port());

        // The limited request must never have reached the upstream.
        assertThat(STUB.received()).hasSize(1);
    }

    // ---- authenticated identity as the key ----

    @Test
    void differentAuthenticatedUsers_doNotShareABucket() {
        String userA = freshUserToken();
        me(userA).exchange().expectStatus().isOk();
        me(userA).exchange().expectStatus().isEqualTo(429); // user A is now exhausted

        String userB = freshUserToken();
        me(userB).exchange().expectStatus().isOk(); // a different user's bucket is untouched
    }

    @Test
    void theSameUserId_isRateLimitedRegardlessOfWhichTokenCarriesIt() {
        // Two distinct, validly-signed tokens for the SAME subject (e.g. the
        // same user logged in from two tabs) must still share one bucket.
        String subject = UUID.randomUUID().toString();
        me(bearer(subject)).exchange().expectStatus().isOk();
        me(bearer(subject)).exchange().expectStatus().isEqualTo(429);
    }

    // ---- unauthenticated traffic falls back to an IP-based key ----

    @Test
    void publicEndpoints_shareOneIpKeyedBucket_acrossDifferentPublicPaths() {
        // register and login are different paths but the same caller (this
        // test's own loopback address) — proving the fallback key is per-IP,
        // not per-route or per-endpoint.
        client.post().uri("/api/auth/register").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange().expectStatus().isOk();

        // login, a DIFFERENT public path, from the same IP — capacity already spent above.
        publicLogin().exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void publicAndAuthenticatedTraffic_useSeparateKeySpaces() {
        publicLogin().exchange().expectStatus().isOk(); // exhausts the IP bucket
        publicLogin().exchange().expectStatus().isEqualTo(429);

        // ...an authenticated user calling from the very same machine is unaffected.
        me(freshUserToken()).exchange().expectStatus().isOk();
    }

    // ---- CORS on a 429 ----

    @Test
    void a429_keepsItsCorsHeaders_soTheBrowserCanReadIt() {
        String auth = freshUserToken();
        me(auth).exchange().expectStatus().isOk();

        EntityExchangeResult<byte[]> limited = me(auth).header("Origin", ALLOWED_ORIGIN).exchange()
                .expectBody().returnResult();

        assertThat(limited.getStatus().value()).isEqualTo(429);
        assertThat(limited.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(limited.getResponseHeaders().get("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void corsPreflight_isUnaffectedByTheRateLimit_andNeverCountsAgainstIt() {
        for (int i = 0; i < BURST_CAPACITY + 2; i++) {
            client.options().uri("/api/users/me")
                    .header("Origin", ALLOWED_ORIGIN)
                    .header("Access-Control-Request-Method", "GET")
                    .exchange().expectStatus().isOk();
        }
        // Preflight never reaches routing (Spring Security answers it), so it
        // never touches the rate limiter; a real request right after still succeeds.
        me(freshUserToken()).exchange().expectStatus().isOk();
    }
}
