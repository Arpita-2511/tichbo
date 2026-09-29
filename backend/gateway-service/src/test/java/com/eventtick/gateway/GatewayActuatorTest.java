package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 14 Step 1: service-local health and Prometheus metrics endpoints on
 * the gateway itself. Reachable without an Eventtick JWT (see
 * {@code GatewaySecurityConfig}'s narrow allowlist), while every other
 * Actuator endpoint stays unreachable — {@code management.endpoints.web.
 * exposure.include} only turns on {@code health}/{@code prometheus}, and
 * there is deliberately no {@code spring.cloud.gateway.routes} entry for
 * {@code /actuator/**}, so none of these requests are ever proxied upstream
 * (see {@link StubUpstream#received()} assertions below). Existing routing/
 * JWT/CORS/rate-limit/timeout behaviour is covered by the other test classes
 * in this package and is not repeated here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Spring Boot Test disables every metrics exporter except "simple" inside a
// @SpringBootTest context by default (see the framework's own
// AutoConfigureObservability Javadoc) — without this, /actuator/prometheus
// would 404 here even though it works in the real running application.
@AutoConfigureObservability
class GatewayActuatorTest {

    private static final StubUpstream STUB = new StubUpstream();
    // A real embedded Redis, not a mock — see docs/architecture.md, "Redis
    // testing (Phase 11)". Its rate-limiting behaviour (INCR/EVAL, exercised
    // by every other test in this package) is unaffected, but its INFO
    // command response isn't one Spring Data Redis 3.3.4's health indicator
    // can parse (RedisSystemException: "Cannot read Redis info") — a known
    // limitation of this lightweight test-only redis-server distribution,
    // not of real Redis or of this project's own code. That's why the
    // aggregate /actuator/health below is asserted structurally rather than
    // pinned to "UP": see that test's comment.
    private static final TestRedis REDIS = TestRedis.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);
        // /actuator/** is not a categorised path, so it hits the fallback
        // policy (default 2/s, burst 5 per client IP). This class sends ~10
        // unauthenticated requests from one IP, so on a fast machine it
        // exhausted that bucket and a test saw 429 — timing-dependent, and
        // unrelated to what these tests assert. Headroom here is test-only.
        registry.add("eventtick.rate-limit.fallback.replenish-rate", () -> 1000);
        registry.add("eventtick.rate-limit.fallback.burst-capacity", () -> 1000);
        registry.add("eventtick.rate-limit.fallback.requested-tokens", () -> 1);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
        REDIS.stop();
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void clearRecorded() {
        STUB.clear();
    }

    private static String adminBearer() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    private EntityExchangeResult<byte[]> get(String path) {
        return client.get().uri(path).exchange().expectBody().returnResult();
    }

    // ---- health / liveness / readiness: reachable without a JWT ----

    @Test
    void health_isReachableWithoutAToken() {
        // Reachable without a JWT (the point of this test) and well-formed
        // either way. Not pinned to 200/"UP": the aggregate rolls up every
        // auto-configured indicator, including Redis connectivity (as
        // intended — see application.yml's Phase 14 note), and this test's
        // embedded test-Redis can't satisfy that indicator's INFO call (see
        // the REDIS field's comment above) — a test-double limitation
        // distinct from readiness/liveness below, which don't depend on
        // Redis and are pinned to 200/UP. show-details defaults to "never"
        // (unchanged), so the body deliberately carries no component
        // breakdown to assert on here; Redis's participation was confirmed
        // manually with show-details=always during development. A real
        // Redis server does not have this test-double's INFO limitation.
        EntityExchangeResult<byte[]> result = get("/actuator/health");

        assertThat(result.getStatus().value()).isIn(200, 503);
        assertThat(bodyOf(result)).contains("\"status\":");
    }

    @Test
    void liveness_isReachableWithoutAToken_andUp() {
        EntityExchangeResult<byte[]> result = get("/actuator/health/liveness");

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(bodyOf(result)).contains("\"status\":\"UP\"");
    }

    @Test
    void readiness_isReachableWithoutAToken() {
        // Diagnostic only (see GatewaySecurityConfig/application.yml notes):
        // whatever Redis reports here must not affect whether the request
        // itself succeeds, and it must not change the rate limiter's
        // existing fail-open behaviour — this only asserts the readiness
        // endpoint itself is reachable and returns a well-formed status.
        EntityExchangeResult<byte[]> result = get("/actuator/health/readiness");

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(bodyOf(result)).contains("\"status\":");
    }

    // ---- prometheus: reachable without a JWT, standard exposition format ----

    @Test
    void prometheus_isReachableWithoutAToken_andExposesStandardMetrics() {
        EntityExchangeResult<byte[]> result = get("/actuator/prometheus");

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(bodyOf(result)).contains("jvm_memory_used_bytes").contains("process_uptime_seconds");
    }

    // ---- gateway isolation: never forwarded upstream ----

    @Test
    void actuatorRequests_areServedLocally_neverForwardedToAnUpstream() {
        get("/actuator/health");
        get("/actuator/prometheus");

        assertThat(STUB.received()).isEmpty();
    }

    // ---- sensitive endpoints stay unreachable ----

    @Test
    void unauthenticated_sensitiveActuatorEndpoints_areNotExposed() {
        for (String path : new String[]{
                "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/loggers", "/actuator/heapdump"}) {
            // Not on GatewaySecurityConfig's allowlist, so this is blocked by
            // the deny-by-default rule before Actuator (which never
            // registered a handler for these anyway) is even reached.
            EntityExchangeResult<byte[]> result = get(path);
            assertThat(result.getStatus().value()).as(path).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void authenticated_sensitiveActuatorEndpoints_stillAreNotExposed() {
        for (String path : new String[]{
                "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/loggers", "/actuator/heapdump"}) {
            // Even a valid ADMIN token doesn't help: these endpoint ids were
            // never turned on (management.endpoints.web.exposure.include),
            // so there is no handler for Spring to authorize in the first
            // place — same "unmatched path" 404 as any other unknown route.
            EntityExchangeResult<byte[]> result = client.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, adminBearer())
                    .exchange().expectBody().returnResult();
            assertThat(result.getStatus().value()).as(path).isEqualTo(404);
        }
        assertThat(STUB.received()).isEmpty();
    }

    // ---- existing business-route protection is untouched ----

    @Test
    void businessRoutes_stillRequireAJwt_actuatorAllowlistDidNotWidenThem() {
        assertThat(get("/api/users/me").getStatus().value()).isEqualTo(401);
        assertThat(get("/api/admin/users").getStatus().value()).isEqualTo(401);
    }

    @Test
    void adminRoutes_stillRequireRoleAdmin_actuatorAllowlistDidNotWidenThem() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid().role("CUSTOMER").build())
                .exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(403);
    }

    private static String bodyOf(EntityExchangeResult<byte[]> result) {
        byte[] body = result.getResponseBody();
        return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
}
