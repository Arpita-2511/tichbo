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
 * Phase 12: dynamic, Redis-backed rate limiting, end to end — a real
 * gateway, a real (embedded, see {@link TestRedis}) Redis, and the recording
 * {@link StubUpstream}. Each test starts from a freshly flushed Redis so
 * bucket state never leaks between tests regardless of execution order.
 *
 * <p>Every policy this class touches is overridden (via
 * {@code eventtick.rate-limit.policies.*}) to a small, distinct
 * burst-capacity with {@code requested-tokens == burst-capacity}, so a
 * single request drains a fresh bucket completely — the same determinism
 * technique Phase 11 settled on: {@code replenish-rate} cannot go below 1
 * token/second, so a per-call cost of 1 token leaves only a one-second
 * margin against a stray refill, which was observed to flake under real
 * load. Distinct capacities per (category, tier) also let a test assert
 * *which* policy applied (via the {@code X-RateLimit-Burst-Capacity}
 * header), not just "some policy did".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRateLimitTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    // One distinct burst-capacity per policy this class exercises, so a
    // response's X-RateLimit-Burst-Capacity header proves which policy
    // actually applied.
    private static final int AUTH_PUBLIC = 2;
    private static final int CATALOG_FREE = 3;
    private static final int CATALOG_PRO = 5;
    private static final int CATALOG_PREMIUM = 6;
    private static final int CATALOG_ADMIN = 7;
    private static final int BOOKING_FREE = 4;
    private static final int FALLBACK = 2;

    private static final StubUpstream STUB = new StubUpstream();
    private static final TestRedis REDIS = TestRedis.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);

        overridePolicy(registry, "AUTH.PUBLIC", AUTH_PUBLIC);
        overridePolicy(registry, "CATALOG.FREE", CATALOG_FREE);
        overridePolicy(registry, "CATALOG.PRO", CATALOG_PRO);
        overridePolicy(registry, "CATALOG.PREMIUM", CATALOG_PREMIUM);
        overridePolicy(registry, "CATALOG.ADMIN", CATALOG_ADMIN);
        overridePolicy(registry, "BOOKING.FREE", BOOKING_FREE);
        registry.add("eventtick.rate-limit.fallback.replenish-rate", () -> 1);
        registry.add("eventtick.rate-limit.fallback.burst-capacity", () -> FALLBACK);
        registry.add("eventtick.rate-limit.fallback.requested-tokens", () -> FALLBACK);
    }

    private static void overridePolicy(DynamicPropertyRegistry registry, String categoryDotTier, int burstCapacity) {
        String prefix = "eventtick.rate-limit.policies." + categoryDotTier + ".";
        registry.add(prefix + "replenish-rate", () -> 1);
        registry.add(prefix + "burst-capacity", () -> burstCapacity);
        registry.add(prefix + "requested-tokens", () -> burstCapacity);
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
        // Warm the whole pipeline (routing, security, policy resolution, the
        // rate limiter's first EVAL of its Lua script, the upstream call)
        // before any timed assertion runs.
        client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange();

        STUB.clear();
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
    }

    private static String freeToken() {
        return "Bearer " + TestTokens.valid().plan("Free").build();
    }

    private static String proToken() {
        return "Bearer " + TestTokens.valid().plan("Pro").build();
    }

    private static String premiumToken() {
        return "Bearer " + TestTokens.valid().plan("Premium").build();
    }

    private static String adminToken() {
        // role wins over plan regardless of what plan an admin happens to have.
        return "Bearer " + TestTokens.valid().role("ADMIN").plan("Free").build();
    }

    private WebTestClient.RequestHeadersSpec<?> catalog(String authorization) {
        return withAuth(client.get().uri("/api/catalog/content"), authorization);
    }

    private WebTestClient.RequestHeadersSpec<?> booking(String authorization) {
        return withAuth(client.get().uri("/api/bookings"), authorization);
    }

    private WebTestClient.RequestHeadersSpec<?> me(String authorization) {
        return withAuth(client.get().uri("/api/users/me"), authorization);
    }

    private WebTestClient.RequestHeadersSpec<?> unknownPath(String authorization) {
        return withAuth(client.get().uri("/api/does-not-exist/x"), authorization);
    }

    private static WebTestClient.RequestHeadersSpec<?> withAuth(WebTestClient.RequestHeadersSpec<?> request, String authorization) {
        return authorization == null ? request : request.header(HttpHeaders.AUTHORIZATION, authorization);
    }

    private WebTestClient.RequestHeadersSpec<?> publicLogin() {
        return client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}");
    }

    private static String burstCapacityOf(EntityExchangeResult<byte[]> result) {
        return result.getResponseHeaders().getFirst("X-RateLimit-Burst-Capacity");
    }

    // ---- tier resolution: FREE / PRO / PREMIUM / ADMIN / public ----

    @Test
    void freeUser_receivesTheCatalogFreePolicy() {
        EntityExchangeResult<byte[]> result = catalog(freeToken()).exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_FREE));
    }

    @Test
    void proUser_receivesTheCatalogProPolicy_distinctFromFree() {
        EntityExchangeResult<byte[]> result = catalog(proToken()).exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_PRO)).isNotEqualTo(String.valueOf(CATALOG_FREE));
    }

    @Test
    void premiumUser_receivesTheCatalogPremiumPolicy() {
        EntityExchangeResult<byte[]> result = catalog(premiumToken()).exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_PREMIUM));
    }

    @Test
    void adminUser_receivesTheCatalogAdminPolicy_regardlessOfTheirPlanClaim() {
        // adminToken() carries plan=Free but role=ADMIN — role must win.
        EntityExchangeResult<byte[]> result = catalog(adminToken()).exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_ADMIN));
    }

    @Test
    void unauthenticatedRequest_receivesThePublicAuthPolicy() {
        EntityExchangeResult<byte[]> result = publicLogin().exchange().expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(AUTH_PUBLIC));
    }

    // ---- bucket separation ----

    @Test
    void freeAndProRequests_fromDifferentUsers_doNotShareABucket() {
        catalog(freeToken()).exchange().expectStatus().isOk();
        // A brand new PRO user's very first catalog call still succeeds —
        // unaffected by the FREE user's bucket above.
        catalog(proToken()).exchange().expectStatus().isOk();
    }

    @Test
    void catalogAndBooking_resolveDifferentPolicies_forTheSameUser() {
        String subject = UUID.randomUUID().toString();
        String auth = "Bearer " + TestTokens.valid().subject(subject).plan("Free").build();

        EntityExchangeResult<byte[]> catalogResult = catalog(auth).exchange().expectBody().returnResult();
        EntityExchangeResult<byte[]> bookingResult = booking(auth).exchange().expectBody().returnResult();

        assertThat(burstCapacityOf(catalogResult)).isEqualTo(String.valueOf(CATALOG_FREE));
        assertThat(burstCapacityOf(bookingResult)).isEqualTo(String.valueOf(BOOKING_FREE)).isNotEqualTo(burstCapacityOf(catalogResult));
    }

    @Test
    void differentPolicies_forTheSameIdentity_useSeparateBuckets_notOne() {
        // Same user, two categories (so two different policies -> two
        // different burst-capacities). Draining the CATALOG bucket must not
        // touch the BOOKING bucket for that same user.
        String subject = UUID.randomUUID().toString();
        String auth = "Bearer " + TestTokens.valid().subject(subject).plan("Free").build();

        catalog(auth).exchange().expectStatus().isOk(); // one call drains the CATALOG bucket (requested-tokens == burst-capacity)
        catalog(auth).exchange().expectStatus().isEqualTo(429); // CATALOG bucket for this user is now exhausted

        // BOOKING, same user: untouched, still succeeds.
        booking(auth).exchange().expectStatus().isOk();
    }

    // ---- authentication endpoints resolve to the public/auth policy ----

    @Test
    void authEndpoints_resolveToThePublicAuthPolicy_notSomeOtherCategory() {
        EntityExchangeResult<byte[]> register = client.post().uri("/api/auth/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}")
                .exchange().expectBody().returnResult();

        assertThat(burstCapacityOf(register)).isEqualTo(String.valueOf(AUTH_PUBLIC));
    }

    // ---- unknown path -> documented fallback ----

    @Test
    void unknownPath_withAValidToken_getsTheFallbackPolicy_notUnlimitedAccess() {
        EntityExchangeResult<byte[]> result = unknownPath(freeToken()).exchange().expectBody().returnResult();

        // The path itself is unrouted, so this is a 404 from GatewayErrorHandler
        // — but the rate limiter still ran first (GlobalFilters run before a
        // route is chosen) and still applied a real, strict policy.
        assertThat(result.getStatus().value()).isEqualTo(404);
        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(FALLBACK));
    }

    @Test
    void unknownPath_isStillRateLimited_notBypassed() {
        String auth = freeToken(); // one identity for the whole test, so it shares one fallback bucket
        unknownPath(auth).exchange().expectStatus().isEqualTo(404); // drains the fallback bucket (requested-tokens == burst-capacity)

        // The next call to the SAME unknown path, same caller, is limited —
        // proving the fallback bucket is real and enforced, not skipped
        // entirely (this would be 404 forever if the limiter never ran at all).
        unknownPath(auth).exchange().expectStatus().isEqualTo(429);
    }

    // ---- claims cannot be spoofed ----

    @Test
    void aClientSuppliedPlanHeader_isIgnored_thePolicyStillComesFromTheJwt() {
        // A FREE-plan JWT, but the caller tries to claim PRO via a header.
        EntityExchangeResult<byte[]> result = catalog(freeToken())
                .header("X-Plan", "PRO")
                .exchange().expectBody().returnResult();

        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_FREE))
                .isNotEqualTo(String.valueOf(CATALOG_PRO));
    }

    @Test
    void aClientSuppliedRoleHeader_isIgnored_cannotSpoofAdmin() {
        // A FREE-plan, CUSTOMER-role JWT, but the caller tries to claim ADMIN via a header.
        EntityExchangeResult<byte[]> result = catalog(freeToken())
                .header("X-Role", "ADMIN")
                .exchange().expectBody().returnResult();

        assertThat(burstCapacityOf(result)).isEqualTo(String.valueOf(CATALOG_FREE))
                .isNotEqualTo(String.valueOf(CATALOG_ADMIN));
    }

    @Test
    void anInvalidJwtClaimingAHighTier_isRejectedBeforeAnyPolicyApplies() {
        // Signed with the wrong secret: JWT authentication (Phase 7.3) rejects
        // this with 401 before the request ever reaches the rate limiter —
        // "claims determine policy only after successful validation" in
        // practice means an invalid token gets no policy-based access at all.
        String forged = "Bearer " + TestTokens.valid().plan("Premium").role("ADMIN")
                .secret(TestTokens.OTHER_SECRET).build();

        client.get().uri("/api/catalog/content").header(HttpHeaders.AUTHORIZATION, forged)
                .exchange().expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }

    // ---- 429 shape, requestId, CORS (unchanged from Phase 11) ----

    @Test
    void a429_stillContainsTheStandardBody_withRequestId() {
        String auth = freeToken();
        catalog(auth).exchange().expectStatus().isOk(); // drains the bucket (requested-tokens == burst-capacity)

        EntityExchangeResult<byte[]> limited = catalog(auth).header("X-Request-ID", "id-for-a-429").exchange()
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
                .doesNotContain("redis").doesNotContain("Redis").doesNotContain("Lua")
                .doesNotContain("Exception").doesNotContain("localhost:" + REDIS.port())
                // Nothing policy-internal leaks into the client-facing body either.
                .doesNotContain("CATALOG:FREE").doesNotContain("policyId");

        assertThat(STUB.received()).hasSize(1);
    }

    @Test
    void a429_keepsItsCorsHeaders_soTheBrowserCanReadIt() {
        String auth = freeToken();
        catalog(auth).exchange().expectStatus().isOk(); // drains the bucket

        EntityExchangeResult<byte[]> limited = catalog(auth).header("Origin", ALLOWED_ORIGIN).exchange()
                .expectBody().returnResult();

        assertThat(limited.getStatus().value()).isEqualTo(429);
        assertThat(limited.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(limited.getResponseHeaders().get("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void corsPreflight_isUnaffectedByTheRateLimit_andNeverCountsAgainstIt() {
        for (int i = 0; i < CATALOG_FREE + 2; i++) {
            client.options().uri("/api/catalog/content")
                    .header("Origin", ALLOWED_ORIGIN)
                    .header("Access-Control-Request-Method", "GET")
                    .exchange().expectStatus().isOk();
        }
        catalog(freeToken()).exchange().expectStatus().isOk();
    }

    // ---- same identity, same policy: bucket state is really shared ----

    @Test
    void twoRequests_sameIdentity_samePolicy_shareOneRedisBucket() {
        String subject = UUID.randomUUID().toString();
        String auth = "Bearer " + TestTokens.valid().subject(subject).plan("Free").build();

        catalog(auth).exchange().expectStatus().isOk(); // drains the CATALOG_FREE bucket
        // A second, distinct request (same identity, same category/tier -> same
        // policy) sees the SAME bucket, already exhausted.
        catalog(auth).exchange().expectStatus().isEqualTo(429);
    }
}
