package com.eventtick.gateway;

import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny in-process stand-in for user/catalog/booking-service: answers 200 to
 * everything and records what the gateway actually sent it, so a test can
 * prove what did (or did not) reach the upstream.
 */
final class StubUpstream {

    /** One request as the upstream saw it. {@code requestIds} keeps every X-Request-ID header value. */
    record Received(String method, String path, String query, List<String> requestIds) {
    }

    /** A request to a path ending in this makes the stub put its own X-Request-ID on the response. */
    static final String SETS_OWN_REQUEST_ID = "/upstream-sets-id";

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    StubUpstream() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            List<String> ids = exchange.getRequestHeaders().get("X-Request-ID");
            received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    ids == null ? List.of() : List.copyOf(ids)));
            byte[] body = "{\"from\":\"stub-upstream\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (exchange.getRequestURI().getPath().endsWith(SETS_OWN_REQUEST_ID)) {
                exchange.getResponseHeaders().set("X-Request-ID", "id-chosen-by-upstream");
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    List<Received> received() {
        return received;
    }

    void clear() {
        received.clear();
    }

    void stop() {
        server.stop(0);
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Points the three gateway routes at {@code upstreamUrl}. A list property is
     * replaced wholesale by a higher-priority source (not merged index by
     * index), so each route is restated in full: same ids and path predicates
     * as application.yml, only the uri differs.
     */
    static void routeEverythingTo(DynamicPropertyRegistry registry, String upstreamUrl) {
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
            registry.add(prefix + "uri", () -> upstreamUrl);
            registry.add(prefix + "predicates[0]", () -> "Path=" + path);
        }
        registry.add("jwt.secret", () -> TestTokens.SECRET);
        registry.add("jwt.issuer", () -> TestTokens.ISSUER);
        registry.add("jwt.audience", () -> TestTokens.AUDIENCE);
    }
}
