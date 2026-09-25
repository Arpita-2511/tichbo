package com.eventtick.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7.3: the gateway authenticates protected requests before forwarding.
 *
 * <p>All three routes are pointed at a small in-process stub "upstream" that
 * records every request it receives. That lets each test prove both halves
 * of the contract: a valid token really is forwarded (the stub saw it), and
 * an invalid one gets a 401 <i>without the upstream ever being contacted</i>.
 *
 * <p>Tokens are minted by {@link TestTokens} with test-only secrets and are
 * never printed or asserted on by value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayJwtAuthenticationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    /** What the stub upstream saw for one request. */
    private record Received(String method, String path, String authorization) {
    }

    private static HttpServer upstream;
    private static final List<Received> received = new CopyOnWriteArrayList<>();

    @BeforeAll
    static synchronized void startUpstream() throws IOException {
        if (upstream != null) {
            return; // the property source below may have started it already
        }
        upstream = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        upstream.createContext("/", exchange -> {
            received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = "{\"from\":\"stub-upstream\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        // The stub must be up before the context reads the route URIs.
        startUpstream();
        String stub = "http://127.0.0.1:" + upstream.getAddress().getPort();
        // A list property is replaced wholesale by a higher-priority source (it
        // is not merged index by index), so each route is restated in full:
        // same ids and path predicates as application.yml, only the uri differs.
        String[][] routes = {
                {"user-service", "/api/auth/**,/api/users/**"},
                {"catalog-service", "/api/catalog/**"},
                {"booking-service", "/api/bookings/**"},
        };
        for (int i = 0; i < routes.length; i++) {
            String prefix = "spring.cloud.gateway.routes[" + i + "].";
            String id = routes[i][0];
            String path = routes[i][1];
            registry.add(prefix + "id", () -> id);
            registry.add(prefix + "uri", () -> stub);
            registry.add(prefix + "predicates[0]", () -> "Path=" + path);
        }
        registry.add("jwt.secret", () -> TestTokens.SECRET);
        registry.add("jwt.issuer", () -> TestTokens.ISSUER);
        registry.add("jwt.audience", () -> TestTokens.AUDIENCE);
    }

    @Autowired
    private WebTestClient client;

    @BeforeEach
    void clearRecorded() {
        received.clear();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private WebTestClient.ResponseSpec getMe(String authorizationHeader) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri("/api/users/me");
        if (authorizationHeader != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return request.exchange();
    }

    private void assertRejectedWithoutReachingUpstream(WebTestClient.ResponseSpec response) {
        response.expectStatus().isUnauthorized();
        assertThat(received).as("upstream must not be contacted for a rejected request").isEmpty();
    }

    // ---- public endpoints ----

    @Test
    void register_isPublic_noTokenNeeded() {
        client.post().uri("/api/auth/register").bodyValue("{}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange()
                .expectStatus().isOk();

        assertThat(received).extracting(Received::method, Received::path)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POST", "/api/auth/register"));
    }

    @Test
    void login_isPublic_noTokenNeeded() {
        client.post().uri("/api/auth/login").bodyValue("{}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange()
                .expectStatus().isOk();

        assertThat(received).extracting(Received::path).containsExactly("/api/auth/login");
    }

    @Test
    void login_ignoresAStaleOrGarbageToken() {
        // A browser holding an expired token must still be able to log in again.
        client.post().uri("/api/auth/login").bodyValue("{}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, bearer("garbage.not.ajwt"))
                .exchange()
                .expectStatus().isOk();

        assertThat(received).hasSize(1);
    }

    @Test
    void publicList_isExactlyPostRegisterAndPostLogin() {
        // Same paths, wrong method: not public.
        assertRejectedWithoutReachingUpstream(client.get().uri("/api/auth/login").exchange());
        assertRejectedWithoutReachingUpstream(client.put().uri("/api/auth/register").exchange());
        // A sibling path under /api/auth is not public either.
        assertRejectedWithoutReachingUpstream(
                client.post().uri("/api/auth/login/extra").bodyValue("{}").exchange());
    }

    // ---- rejected: 401 and the upstream is never contacted ----

    @Test
    void me_withoutToken_is401() {
        assertRejectedWithoutReachingUpstream(getMe(null));
    }

    @Test
    void me_withMalformedToken_is401() {
        assertRejectedWithoutReachingUpstream(getMe(bearer("this-is-not-a-jwt")));
        assertRejectedWithoutReachingUpstream(getMe(bearer("a.b.c")));
    }

    @Test
    void me_withNonBearerScheme_is401() {
        assertRejectedWithoutReachingUpstream(
                getMe("Basic " + java.util.Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void me_withInvalidSignature_is401() {
        String token = TestTokens.valid().secret(TestTokens.OTHER_SECRET).build();
        assertRejectedWithoutReachingUpstream(getMe(bearer(token)));
    }

    @Test
    void me_withTamperedPayload_is401() {
        String token = TestTokens.valid().build();
        String[] parts = token.split("\\.");
        // Flip a character in the payload so the signature no longer matches.
        char[] payload = parts[1].toCharArray();
        payload[5] = payload[5] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];
        assertRejectedWithoutReachingUpstream(getMe(bearer(tampered)));
    }

    @Test
    void me_withExpiredToken_is401() {
        String token = TestTokens.valid().expiresAt(Instant.now().minus(Duration.ofSeconds(5))).build();
        assertRejectedWithoutReachingUpstream(getMe(bearer(token)));
    }

    @Test
    void me_withoutExpClaim_is401() {
        assertRejectedWithoutReachingUpstream(getMe(bearer(TestTokens.valid().neverExpires().build())));
    }

    @Test
    void me_withWrongIssuer_is401() {
        assertRejectedWithoutReachingUpstream(
                getMe(bearer(TestTokens.valid().issuer("someone-else").build())));
    }

    @Test
    void me_withWrongAudience_is401() {
        assertRejectedWithoutReachingUpstream(
                getMe(bearer(TestTokens.valid().audience("some-other-audience").build())));
    }

    @Test
    void me_withUnsignedAlgNoneToken_is401() {
        assertRejectedWithoutReachingUpstream(getMe(bearer(TestTokens.valid().buildUnsigned())));
    }

    @Test
    void me_withSubjectThatIsNotAUuid_is401() {
        assertRejectedWithoutReachingUpstream(
                getMe(bearer(TestTokens.valid().subject("not-a-uuid").build())));
    }

    @Test
    void me_withUnknownRole_is401() {
        assertRejectedWithoutReachingUpstream(
                getMe(bearer(TestTokens.valid().role("SUPERUSER").build())));
    }

    // ---- accepted: forwarded to the upstream ----

    @Test
    void me_withValidToken_isForwardedWithTheAuthorizationHeader() {
        String token = TestTokens.valid().build();

        getMe(bearer(token)).expectStatus().isOk();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).path()).isEqualTo("/api/users/me");
        // user-service re-validates the token itself, so it must arrive intact.
        assertThat(received.get(0).authorization()).isEqualTo(bearer(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "HS384", "HS512"})
    void me_acceptsTheWholeHmacFamily(String algorithm) {
        // user-service lets JJWT pick HS256/384/512 by secret length, so the
        // gateway must accept whichever one the deployed secret produces.
        String token = TestTokens.valid().algorithm(JWSAlgorithm.parse(algorithm)).build();

        getMe(bearer(token)).expectStatus().isOk();
        assertThat(received).hasSize(1);
    }

    @Test
    void adminToken_isAlsoAccepted() {
        getMe(bearer(TestTokens.valid().role("ADMIN").build())).expectStatus().isOk();
        assertThat(received).hasSize(1);
    }

    // ---- catalog / booking: authenticated by default, no role policy yet ----

    @Test
    void catalogAndBooking_requireAValidToken() {
        assertRejectedWithoutReachingUpstream(client.get().uri("/api/catalog/content").exchange());
        assertRejectedWithoutReachingUpstream(client.get().uri("/api/bookings").exchange());
        assertRejectedWithoutReachingUpstream(client.get().uri("/api/bookings/shows/1/seats").exchange());
    }

    @Test
    void catalogAndBooking_withValidToken_areForwarded() {
        String header = bearer(TestTokens.valid().build());

        client.get().uri("/api/catalog/content").header(HttpHeaders.AUTHORIZATION, header)
                .exchange().expectStatus().isOk();
        client.get().uri("/api/bookings").header(HttpHeaders.AUTHORIZATION, header)
                .exchange().expectStatus().isOk();

        assertThat(received).extracting(Received::path)
                .containsExactly("/api/catalog/content", "/api/bookings");
    }

    @Test
    void unroutedPath_withoutToken_isStill401_notLeakedAs404() {
        // Default-deny: authentication runs before routing, so an attacker
        // can't probe which paths exist.
        assertRejectedWithoutReachingUpstream(client.get().uri("/api/does-not-exist").exchange());
    }

    // ---- shape of the 401 ----

    @Test
    void unauthorizedResponse_isJson_andRevealsNoReason() {
        String expired = TestTokens.valid().expiresAt(Instant.now().minusSeconds(60)).build();

        client.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expired))
                .exchange()
                .expectStatus().isUnauthorized()
                // Bare challenge: Spring's default would add error_description
                // ("Jwt expired at ..."), which tells a caller why.
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.timestamp").exists()
                .consumeWith(result -> {
                    String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
                    assertThat(body).doesNotContainIgnoringCase("expired").doesNotContain(expired);
                });
    }

    // ---- CORS is preserved ----

    @Test
    void preflight_isAnsweredWithoutAToken_andNeverReachesUpstream() {
        client.options().uri("/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().valueEquals("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");

        assertThat(received).isEmpty();
    }

    @Test
    void a401_carriesCorsHeaders_soTheBrowserFrontendCanReadIt() {
        // Without this the browser hides the 401 as a generic network error and
        // the frontend could not tell "token expired" from "gateway down".
        client.get().uri("/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials");
    }

    @Test
    void forwardedResponse_carriesExactlyOneAllowOriginHeader() {
        client.get().uri("/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.valid().build()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    }

    @Test
    void requestFromUnknownOrigin_isRejected() {
        client.method(HttpMethod.GET).uri("/api/users/me")
                .header("Origin", "http://evil.example")
                .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.valid().build()))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(received).isEmpty();
    }
}
