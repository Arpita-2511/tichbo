package com.eventtick.gateway;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * A real {@code redis-server} process, started for the duration of a test
 * class — not a hand-rolled fake of the Redis protocol. There is no Docker
 * or WSL distro in this development environment (so Testcontainers is not an
 * option here), so tests instead run the genuine native binary that
 * {@code com.github.codemonstur:embedded-redis} bundles for the current OS,
 * exactly the way a developer's own {@code redis-server} would run, just
 * disposable and test-scoped. See {@code docs/architecture.md}, "Redis
 * testing (Phase 11)", for the full reasoning.
 */
final class TestRedis {

    private final RedisServer server;
    private final int port;

    private TestRedis(RedisServer server, int port) {
        this.server = server;
        this.port = port;
    }

    static TestRedis start() {
        int port = freePort();
        try {
            RedisServer server = new RedisServer(port);
            server.start();
            return new TestRedis(server, port);
        } catch (IOException e) {
            throw new UncheckedIOException("could not start the embedded test redis-server", e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String host() {
        return "127.0.0.1";
    }

    int port() {
        return port;
    }

    void stop() {
        try {
            server.stop();
        } catch (IOException e) {
            // Best-effort cleanup at the end of a test class; nothing downstream
            // depends on this succeeding.
        }
    }
}
