package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13.3: the {@code /api/admin/**} authorization rule added to
 * {@code GatewaySecurityConfig.protectedEndpoints} — an {@code ADMIN} token
 * is authorized, a {@code CUSTOMER} token is authenticated but not
 * authorized ({@code 403}, via the existing {@code JsonAccessDeniedHandler}),
 * and a missing/invalid token is still {@code 401} (the existing
 * {@code JsonAuthenticationEntryPoint} — unchanged). Existing non-admin
 * protected endpoints are unaffected.
 *
 * <p>No Redis is configured for this class, the same way
 * {@code GatewayJwtAuthenticationTest}/{@code GatewayRoutesTest} don't:
 * {@code RedisRateLimiter} fails open against an unreachable default
 * ({@code localhost:6379}), so every request here is allowed through
 * rate-limiting regardless of the (untouched) Phase 12 policy matrix — this
 * class is about authorization, not rate limiting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAdminAuthorizationTest {

    private static final StubUpstream STUB = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
        // A 4th, test-only route: production application.yml intentionally
        // has no /api/admin/** route yet (no admin API exists — see
        // docs/architecture.md §45). Adding it only for this test proves the
        // authorization rule itself without introducing a real admin route.
        registry.add("spring.cloud.gateway.routes[3].id", () -> "admin-test-route");
        registry.add("spring.cloud.gateway.routes[3].uri", STUB::url);
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/api/admin/**");
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

    private static String adminToken() {
        return "Bearer " + TestTokens.valid().role("ADMIN").build();
    }

    private static String customerToken() {
        return "Bearer " + TestTokens.valid().role("CUSTOMER").build();
    }

    // ---- A: ADMIN reaches the route/filter chain ----

    @Test
    void adminJwt_requestingAdminPath_isAuthorized_andReachesTheUpstream() {
        client.get().uri("/api/admin/anything")
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).path()).isEqualTo("/api/admin/anything");
    }

    // ---- B: CUSTOMER is authenticated but not authorized ----

    @Test
    void customerJwt_requestingAdminPath_is403_withTheExistingAccessDeniedBody() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/admin/anything")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .header("X-Request-ID", "id-for-a-403")
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(403);
        assertThat(result.getResponseHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        assertThat(result.getResponseHeaders().get("X-Request-ID")).containsExactly("id-for-a-403");

        String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":403")
                .contains("\"error\":\"FORBIDDEN\"")
                .contains("\"requestId\":\"id-for-a-403\"")
                .contains("\"timestamp\"")
                // JsonAccessDeniedHandler's existing, unchanged message — not a new one.
                .contains("You do not have permission to perform this action.");

        // Not authorized -> never forwarded.
        assertThat(STUB.received()).isEmpty();
    }

    // ---- C: missing token is still 401, via the existing entry point ----

    @Test
    void missingJwt_requestingAdminPath_is401_withTheExistingAuthenticationEntryPointBody() {
        EntityExchangeResult<byte[]> result = client.get().uri("/api/admin/anything")
                .header("X-Request-ID", "id-for-a-401")
                .exchange()
                .expectBody().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(401);
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(result.getResponseHeaders().get("X-Request-ID")).containsExactly("id-for-a-401");

        String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"status\":401")
                .contains("\"error\":\"UNAUTHENTICATED\"")
                .contains("\"requestId\":\"id-for-a-401\"");

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void invalidJwt_requestingAdminPath_isAlso401_beforeAnyAuthorizationCheck() {
        // Wrong secret: a forged token, even one claiming role=ADMIN, must
        // never reach the authorization rule at all.
        String forged = "Bearer " + TestTokens.valid().role("ADMIN").secret(TestTokens.OTHER_SECRET).build();

        client.get().uri("/api/admin/anything")
                .header(HttpHeaders.AUTHORIZATION, forged)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(STUB.received()).isEmpty();
    }

    // ---- D: existing non-admin protected endpoints unaffected ----

    @Test
    void customerJwt_onExistingNonAdminEndpoints_stillWorks() {
        client.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/catalog/content")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/bookings")
                .header(HttpHeaders.AUTHORIZATION, customerToken())
                .exchange()
                .expectStatus().isOk();

        assertThat(STUB.received()).hasSize(3);
    }

    @Test
    void publicAuthEndpoints_areStillUnaffected_byTheAdminRule() {
        client.post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }
}
