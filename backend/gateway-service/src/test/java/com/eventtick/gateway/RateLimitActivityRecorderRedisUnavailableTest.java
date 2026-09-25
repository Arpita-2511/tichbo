package com.eventtick.gateway;

import com.eventtick.gateway.ratelimit.RateLimitActivityRecorder;
import com.eventtick.gateway.ratelimit.dto.RateLimitActivitySection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-40: {@link RateLimitActivityRecorder} when Redis is unreachable — the
 * same "real refused connection" technique
 * {@code GatewayRateLimitRedisUnavailableTest} already uses for the rate
 * limiter itself. {@link RateLimitActivityRecorder#readActivitySection}
 * must degrade gracefully ({@code redisAvailable = false}, empty map)
 * rather than erroring, and {@link RateLimitActivityRecorder#recordAllowed}/
 * {@link RateLimitActivityRecorder#recordRejected} must never throw.
 */
@SpringBootTest
class RateLimitActivityRecorderRedisUnavailableTest {

    private static final int DEAD_REDIS_PORT = unusedPort();

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> DEAD_REDIS_PORT);
    }

    @Autowired
    private RateLimitActivityRecorder recorder;

    @Test
    void readActivitySection_whenRedisIsUnreachable_returnsRedisAvailableFalse_withAnEmptyMap_neverErrors() {
        RateLimitActivitySection section = recorder.readActivitySection(List.of("CATALOG:FREE", "FALLBACK"))
                .block(Duration.ofSeconds(5));

        assertThat(section).isNotNull();
        assertThat(section.redisAvailable()).isFalse();
        assertThat(section.byPolicy()).isEmpty();
    }

    @Test
    void recordAllowed_whenRedisIsUnreachable_doesNotThrow() {
        // Fire-and-forget: must not raise on the calling thread even though
        // the underlying INCR will fail.
        recorder.recordAllowed("CATALOG:FREE");
        recorder.recordRejected("CATALOG:FREE");
    }
}
