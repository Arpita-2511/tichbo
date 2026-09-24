package com.eventtick.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.4: request correlation ids and the gateway's own error responses,
 * exercised through a real gateway against a recording stub upstream.
 *
 * <p>Tokens come from {@link TestTokens} (test-only secrets) and are never
 * printed. The JWT rules themselves are covered by
 * {@link GatewayJwtAuthenticationTest}; here JWT only matters as far as
 * "does the request id survive it".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GatewayRequestIdTest {

    private static final String HEADER = "X-Request-ID";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private static final StubUpstream STUB = new StubUpstream();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StubUpstream.routeEverythingTo(registry, STUB.url());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void clearRecorded() {
        STUB.clear();
    }

    private static String bearer() {
        return "Bearer " + TestTokens.valid().build();
    }

    /** Runs the request to completion and returns headers + body for inspection. */
    private static EntityExchangeResult<byte[]> complete(WebTestClient.ResponseSpec response) {
        return response.expectBody().returnResult();
    }

    private static List<String> requestIdsOf(EntityExchangeResult<byte[]> result) {
        List<String> ids = result.getResponseHeaders().get(HEADER);
        return ids == null ? List.of() : ids;
    }

    private static String bodyOf(EntityExchangeResult<byte[]> result) {
        byte[] body = result.getResponseBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private WebTestClient.RequestHeadersSpec<?> get(String path) {
        return client.get().uri(path);
    }

    // ---- generation and preservation ----

    @Test
    void requestWithoutId_getsAGeneratedUuid_onTheResponse_andUpstream() {
        EntityExchangeResult<byte[]> result = complete(post("/api/auth/login").exchange());

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(requestIdsOf(result)).hasSize(1);
        String id = requestIdsOf(result).get(0);
        assertThat(UUID.fromString(id)).isNotNull(); // throws if it is not a UUID

        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().get(0).requestIds()).containsExactly(id);
    }

    @Test
    void twoRequestsWithoutId_getDifferentIds() {
        String first = requestIdsOf(complete(post("/api/auth/login").exchange())).get(0);
        String second = requestIdsOf(complete(post("/api/auth/login").exchange())).get(0);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void suppliedId_isPreserved_onTheResponse_andUpstream_withoutASecondOne() {
        EntityExchangeResult<byte[]> result =
                complete(post("/api/auth/login").header(HEADER, "client-supplied-id-123").exchange());

        assertThat(requestIdsOf(result)).containsExactly("client-supplied-id-123");
        assertThat(STUB.received().get(0).requestIds()).containsExactly("client-supplied-id-123");
    }

    @Test
    void severalIdHeaders_collapseToOne_forwardedAndReturned() {
        EntityExchangeResult<byte[]> result = complete(
                post("/api/auth/login").header(HEADER, "first-id").header(HEADER, "second-id").exchange());

        assertThat(requestIdsOf(result)).containsExactly("first-id");
        assertThat(STUB.received().get(0).requestIds()).containsExactly("first-id");
    }

    @Test
    void unsafeSuppliedIds_areReplacedByAGeneratedOne_notEchoed() {
        // Spaces, markup, and an over-long value: none may reach a log line or another service.
        for (String unsafe : new String[]{"has spaces in it", "<script>alert(1)</script>", "x".repeat(129), "id;with=odd,chars"}) {
            STUB.clear();
            EntityExchangeResult<byte[]> result = complete(post("/api/auth/login").header(HEADER, unsafe).exchange());

            assertThat(result.getStatus().value()).as("request still succeeds").isEqualTo(200);
            assertThat(requestIdsOf(result)).hasSize(1);
            String id = requestIdsOf(result).get(0);
            assertThat(id).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(id)).isNotNull();
            assertThat(STUB.received().get(0).requestIds()).containsExactly(id);
        }
    }

    @Test
    void anIdTheUpstreamPutOnItsOwnResponse_doesNotReplaceOrDuplicateTheGatewaysId() {
        EntityExchangeResult<byte[]> result = complete(get("/api/catalog" + StubUpstream.SETS_OWN_REQUEST_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HEADER, "gateway-known-id")
                .exchange());

        assertThat(requestIdsOf(result)).containsExactly("gateway-known-id");
    }

    // ---- reaches every upstream ----

    @Test
    void theSameIdIsForwardedToUserCatalogAndBookingService() {
        for (String path : new String[]{"/api/users/me", "/api/catalog/content", "/api/bookings"}) {
            EntityExchangeResult<byte[]> result = complete(get(path)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .header(HEADER, "trace-for-" + path.hashCode())
                    .exchange());

            assertThat(result.getStatus().value()).as(path).isEqualTo(200);
            assertThat(requestIdsOf(result)).as(path).containsExactly("trace-for-" + path.hashCode());
        }
        assertThat(STUB.received()).extracting(StubUpstream.Received::path)
                .containsExactly("/api/users/me", "/api/catalog/content", "/api/bookings");
        assertThat(STUB.received()).allSatisfy(r ->
                assertThat(r.requestIds()).hasSize(1).first().asString().startsWith("trace-for-"));
    }

    // ---- 401s (Phase 7.3 behaviour must keep working, now with an id) ----

    @Test
    void a401_carriesTheRequestId_inTheHeader_andInTheBody() {
        EntityExchangeResult<byte[]> result = complete(get("/api/users/me").header(HEADER, "id-for-a-401").exchange());

        assertThat(result.getStatus().value()).isEqualTo(401);
        assertThat(requestIdsOf(result)).containsExactly("id-for-a-401");
        assertThat(bodyOf(result)).contains("\"requestId\":\"id-for-a-401\"").contains("\"error\":\"UNAUTHENTICATED\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void a401_withoutASuppliedId_getsAGeneratedOne_thatMatchesTheBody() {
        EntityExchangeResult<byte[]> result = complete(get("/api/users/me").exchange());

        assertThat(result.getStatus().value()).isEqualTo(401);
        String id = requestIdsOf(result).get(0);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(bodyOf(result)).contains("\"requestId\":\"" + id + "\"");
    }

    @Test
    void invalidAndExpiredTokens_stillGet401WithAnId_andNeverReachUpstream() {
        String invalid = TestTokens.valid().secret(TestTokens.OTHER_SECRET).build();
        String expired = TestTokens.valid().expiresAt(Instant.now().minus(Duration.ofSeconds(30))).build();

        for (String token : new String[]{invalid, expired, "not-a-jwt"}) {
            EntityExchangeResult<byte[]> result = complete(get("/api/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange());

            assertThat(result.getStatus().value()).isEqualTo(401);
            assertThat(requestIdsOf(result)).hasSize(1);
            assertThat(bodyOf(result)).doesNotContain(token);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void a401_keepsItsCorsHeaders_soTheBrowserFrontendCanReadIt() {
        EntityExchangeResult<byte[]> result = complete(get("/api/users/me").header("Origin", ALLOWED_ORIGIN).exchange());

        assertThat(result.getStatus().value()).isEqualTo(401);
        HttpHeaders headers = result.getResponseHeaders();
        assertThat(headers.get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(headers.get("Access-Control-Allow-Credentials")).isNull();
        // ...and the id is readable by frontend JavaScript, not just visible in devtools.
        assertThat(headers.getAccessControlExposeHeaders()).contains(HEADER);
        assertThat(requestIdsOf(result)).hasSize(1);
    }

    // ---- public + authenticated paths still work ----

    @Test
    void publicRegisterAndLogin_stillWorkWithoutAToken() {
        assertThat(complete(post("/api/auth/register").exchange()).getStatus().value()).isEqualTo(200);
        assertThat(complete(post("/api/auth/login").exchange()).getStatus().value()).isEqualTo(200);
        assertThat(STUB.received()).extracting(StubUpstream.Received::path)
                .containsExactly("/api/auth/register", "/api/auth/login");
    }

    @Test
    void validJwt_stillWorks_andCorsHeadersStayOnTheProxiedResponse() {
        EntityExchangeResult<byte[]> result = complete(get("/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange());

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(requestIdsOf(result)).hasSize(1);
    }

    @Test
    void corsPreflight_stillWorks_withoutAToken_andNeverReachesUpstream() {
        EntityExchangeResult<byte[]> result = complete(client.options().uri("/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange());

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);
        assertThat(STUB.received()).isEmpty();
    }

    // ---- unknown route ----

    @Test
    void unknownRoute_withAValidToken_isAControlled404_withIdAndCors() {
        EntityExchangeResult<byte[]> result = complete(get("/api/does-not-exist")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header("Origin", ALLOWED_ORIGIN)
                .header(HEADER, "id-for-a-404")
                .exchange());

        assertThat(result.getStatus().value()).isEqualTo(404);
        assertThat(result.getResponseHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        assertThat(requestIdsOf(result)).containsExactly("id-for-a-404");
        assertThat(result.getResponseHeaders().get("Access-Control-Allow-Origin")).containsExactly(ALLOWED_ORIGIN);

        String body = bodyOf(result);
        assertThat(body)
                .contains("\"status\":404")
                .contains("\"error\":\"NOT_FOUND\"")
                .contains("\"message\":\"The requested resource was not found.\"")
                .contains("\"requestId\":\"id-for-a-404\"")
                .contains("\"timestamp\"")
                // Nothing that describes the request or the implementation:
                .doesNotContain("/api/does-not-exist")
                .doesNotContain("Exception")
                .doesNotContain("org.springframework")
                .doesNotContain("trace");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void unknownRoute_withoutAToken_isStill401_notAnAnonymous404() {
        // Default-deny from Phase 7.3: an anonymous caller can't probe which paths exist.
        EntityExchangeResult<byte[]> result = complete(get("/api/does-not-exist").exchange());

        assertThat(result.getStatus().value()).isEqualTo(401);
        assertThat(requestIdsOf(result)).hasSize(1);
    }

    // ---- logging: safe metadata only ----

    @Test
    void requestLogLine_hasTheIdMethodPathAndStatus_butNeverTheQueryOrTheToken(CapturedOutput output) {
        String token = TestTokens.valid().build();

        complete(get("/api/catalog/content?apiKey=query-secret-value")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HEADER, "id-to-find-in-the-log")
                .exchange());

        assertThat(output.getAll())
                .contains("id=id-to-find-in-the-log")
                .contains("method=GET")
                .contains("path=/api/catalog/content")
                .contains("status=200")
                .contains("durationMs=")
                .doesNotContain("query-secret-value")
                .doesNotContain("apiKey")
                .doesNotContain(token)
                .doesNotContain("Bearer");
    }

    private WebTestClient.RequestHeadersSpec<?> post(String path) {
        return client.post().uri(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}");
    }
}
