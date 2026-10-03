package com.eventtick.gateway;

import com.eventtick.gateway.ratelimit.DynamicPolicyService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 19: proves that policy changes via the admin CRUD API take effect
 * on the <b>same running Gateway process</b> — no restart, rebuild,
 * application.yml reload, Redis restart, or ApplicationContext recreation.
 *
 * <p>Lifecycle (each test):
 * <ol>
 *   <li>Gateway is already running (started once by {@code @SpringBootTest}).</li>
 *   <li>Observe the initial policy (seeded from application.yml).</li>
 *   <li>Call the admin API to update the policy.</li>
 *   <li>Send a new request and verify the updated policy is enforced.</li>
 * </ol>
 *
 * <p>The Gateway JVM never stops between steps 2 and 4 — this is the
 * definition of hot reload.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DynamicPolicyHotReloadTest {

    private static final StubUpstream STUB = new StubUpstream();
    private static final TestRedis REDIS = TestRedis.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);
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

    @Autowired
    private DynamicPolicyService policyService;

    @BeforeEach
    void freshState() {
        client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange();
        STUB.clear();
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
        policyService.loadOrSeed().block();
    }

    private static String adminToken() {
        return "Bearer " + TestTokens.valid().role("ADMIN").plan("Free").build();
    }

    private static String freeToken() {
        return "Bearer " + TestTokens.valid().plan("Free").build();
    }

    private static String burstCapacityOf(EntityExchangeResult<byte[]> result) {
        return result.getResponseHeaders().getFirst("X-RateLimit-Burst-Capacity");
    }

    @Test
    void updatePolicy_takesEffectImmediately_withoutRestart() {
        String auth = adminToken();

        // 1. Observe the initial CATALOG:ADMIN burst capacity (from application.yml: 200)
        EntityExchangeResult<byte[]> initial = client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectBody().returnResult();
        String initialBurst = burstCapacityOf(initial);
        assertThat(initialBurst).isEqualTo("200");

        // 2. Find the CATALOG:ADMIN policy id via the list endpoint
        String policyId = client.get().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(policyId).contains("CATALOG").contains("ADMIN");

        // Extract the id of CATALOG:ADMIN from the JSON array
        String id = extractPolicyId(policyId, "CATALOG", "ADMIN");
        assertThat(id).isNotNull();

        // 3. Update the policy: change burst capacity to 999
        client.put().uri("/api/admin/rate-limits/policies/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"replenishRate\":100,\"burstCapacity\":999,\"requestedTokens\":1,\"enabled\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.burstCapacity").isEqualTo(999);

        // 4. Same running process — verify the updated policy takes effect
        EntityExchangeResult<byte[]> updated = client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange().expectBody().returnResult();
        assertThat(burstCapacityOf(updated)).isEqualTo("999");
    }

    @Test
    void createNewPolicy_isEnforcedImmediately_thenDeleteFallsBackToDefault() {
        // BOOKING:FREE is already seeded. Get its initial burst capacity.
        String freeAuth = freeToken();
        EntityExchangeResult<byte[]> beforeCreate = client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, freeAuth)
                .exchange().expectBody().returnResult();
        String initialBurst = burstCapacityOf(beforeCreate);

        // Delete the existing BOOKING:FREE policy
        String auth = adminToken();
        String listBody = client.get().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        String bookingFreeId = extractPolicyId(listBody, "BOOKING", "FREE");
        assertThat(bookingFreeId).isNotNull();

        client.delete().uri("/api/admin/rate-limits/policies/{id}", bookingFreeId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange()
                .expectStatus().isNoContent();

        // After deletion, BOOKING:FREE should fall back to the FALLBACK policy
        EntityExchangeResult<byte[]> afterDelete = client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, freeToken())
                .exchange().expectBody().returnResult();
        String fallbackBurst = burstCapacityOf(afterDelete);
        assertThat(fallbackBurst).isNotEqualTo(initialBurst);

        // Re-create BOOKING:FREE with a distinctive burst capacity
        client.post().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"BOOKING\",\"tier\":\"FREE\",\"replenishRate\":1,\"burstCapacity\":777,\"requestedTokens\":1}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.burstCapacity").isEqualTo(777);

        // The new policy takes effect immediately
        EntityExchangeResult<byte[]> afterRecreate = client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, freeToken())
                .exchange().expectBody().returnResult();
        assertThat(burstCapacityOf(afterRecreate)).isEqualTo("777");
    }

    @Test
    void disablingPolicy_causesImmediateFallback_reEnablingRestoresIt() {
        String auth = adminToken();

        // Find USER:FREE policy
        String listBody = client.get().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        String userFreeId = extractPolicyId(listBody, "USER", "FREE");
        assertThat(userFreeId).isNotNull();

        // Observe USER:FREE burst capacity
        EntityExchangeResult<byte[]> before = client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, freeToken())
                .exchange().expectBody().returnResult();
        String originalBurst = burstCapacityOf(before);

        // Disable the policy (enabled=false)
        client.put().uri("/api/admin/rate-limits/policies/{id}", userFreeId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"replenishRate\":5,\"burstCapacity\":10,\"requestedTokens\":1,\"enabled\":false}")
                .exchange()
                .expectStatus().isOk();

        // With policy disabled, should fall back to FALLBACK
        EntityExchangeResult<byte[]> disabled = client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, freeToken())
                .exchange().expectBody().returnResult();
        assertThat(burstCapacityOf(disabled)).isNotEqualTo(originalBurst);

        // Re-enable the policy with a new burst capacity
        client.put().uri("/api/admin/rate-limits/policies/{id}", userFreeId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"replenishRate\":5,\"burstCapacity\":333,\"requestedTokens\":1,\"enabled\":true}")
                .exchange()
                .expectStatus().isOk();

        // Policy is immediately enforced again with new values
        EntityExchangeResult<byte[]> reEnabled = client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, freeToken())
                .exchange().expectBody().returnResult();
        assertThat(burstCapacityOf(reEnabled)).isEqualTo("333");
    }

    @Test
    void adminCrud_requiresAdminRole() {
        // Anonymous — rejected
        client.get().uri("/api/admin/rate-limits/policies")
                .exchange()
                .expectStatus().isUnauthorized();

        // CUSTOMER — rejected
        String customerAuth = "Bearer " + TestTokens.valid().role("CUSTOMER").plan("Pro").build();
        client.get().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, customerAuth)
                .exchange()
                .expectStatus().isForbidden();

        // ADMIN — allowed
        client.get().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void duplicatePolicy_isRejected() {
        String auth = adminToken();
        // CATALOG:FREE already exists (seeded)
        client.post().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"CATALOG\",\"tier\":\"FREE\",\"replenishRate\":10,\"burstCapacity\":20,\"requestedTokens\":1}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void invalidCategory_isRejected() {
        client.post().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"INVALID\",\"tier\":\"FREE\",\"replenishRate\":10,\"burstCapacity\":20,\"requestedTokens\":1}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void invalidTier_isRejected() {
        client.post().uri("/api/admin/rate-limits/policies")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"category\":\"CATALOG\",\"tier\":\"INVALID\",\"replenishRate\":10,\"burstCapacity\":20,\"requestedTokens\":1}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deleteNonexistent_returns404() {
        client.delete().uri("/api/admin/rate-limits/policies/{id}", "00000000-0000-0000-0000-000000000000")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Extracts a policy id from the JSON list response by category and tier.
     * Simple string parsing to avoid adding Jackson to the test classpath.
     */
    private static String extractPolicyId(String json, String category, String tier) {
        int searchStart = 0;
        while (true) {
            int catIdx = json.indexOf("\"category\":\"" + category + "\"", searchStart);
            if (catIdx == -1) return null;

            int tierIdx = json.indexOf("\"tier\":\"" + tier + "\"", catIdx);
            if (tierIdx == -1) return null;

            int nextBrace = json.indexOf("}", catIdx);
            if (tierIdx > nextBrace) {
                searchStart = nextBrace;
                continue;
            }

            int idIdx = json.lastIndexOf("\"id\":\"", catIdx);
            if (idIdx == -1) {
                idIdx = json.indexOf("\"id\":\"", Math.max(0, catIdx - 200));
                if (idIdx == -1) return null;
            }
            int idStart = idIdx + 6;
            int idEnd = json.indexOf("\"", idStart);
            return json.substring(idStart, idEnd);
        }
    }
}
