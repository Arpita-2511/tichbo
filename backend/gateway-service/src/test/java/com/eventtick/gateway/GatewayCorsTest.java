package com.eventtick.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Phase 7.2: what the browser frontend (Vite dev server, port 5173) needs
 * from the gateway. Preflight requests are answered by the gateway itself,
 * so no upstream service has to be running.
 *
 * <p>Duplicate-header handling (gateway + user-service both adding
 * {@code Access-Control-Allow-Origin}) needs a real proxied response, so it
 * isn't unit-tested here — the {@code DedupeResponseHeader} filter's
 * presence is asserted in {@code GatewayRoutesTest}, and the real
 * end-to-end behavior was verified against a running user-service.
 */
@SpringBootTest
@AutoConfigureWebTestClient
class GatewayCorsTest {

    // Full URLs, not bare paths: CORS handling compares the Origin header to the
    // request's own scheme/host, and the in-memory test client has none by default.
    private static final String GATEWAY = "http://localhost:8080";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private WebTestClient client;

    @Test
    void preflightForLogin_fromAllowedOrigin_isAccepted() {
        client.options().uri(GATEWAY + "/api/auth/login")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .expectHeader().valueEquals("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    }

    @Test
    void preflightWithAuthorizationHeader_isAccepted() {
        client.options().uri(GATEWAY + "/api/users/me")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    }

    @Test
    void alternateLocalhostOrigin_isAccepted() {
        client.options().uri(GATEWAY + "/api/auth/login")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:5173");
    }

    @Test
    void preflight_fromUnknownOrigin_isRejected() {
        client.options().uri(GATEWAY + "/api/auth/login")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void credentialsAreNotAllowed_andNoWildcardOrigin() {
        // Auth is a Bearer header, not a cookie — credentials stay off, and
        // the allowed origin is echoed exactly, never "*".
        client.options().uri(GATEWAY + "/api/auth/login")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials")
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    }
}
