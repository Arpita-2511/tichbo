package com.eventtick.gateway;

import com.eventtick.gateway.ratelimit.RateLimitActivityRecorder;
import com.eventtick.gateway.ratelimit.dto.RateLimitActivityDto;
import com.eventtick.gateway.ratelimit.dto.RateLimitActivitySection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-40: {@link RateLimitActivityRecorder} against a real, embedded Redis
 * (see {@link TestRedis} — the same "real redis-server process" technique
 * {@code GatewayRateLimitTest} already uses) — this proves the actual
 * namespace/{@code INCR} behavior, not a mocked approximation of it.
 */
@SpringBootTest
class RateLimitActivityRecorderTest {

    private static final TestRedis REDIS = TestRedis.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::host);
        registry.add("spring.data.redis.port", REDIS::port);
    }

    @AfterAll
    static void stopInfrastructure() {
        REDIS.stop();
    }

    @Autowired
    private RateLimitActivityRecorder recorder;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void freshState() {
        redisConnectionFactory.getReactiveConnection().serverCommands().flushAll().block();
    }

    private void awaitWrite() {
        // recordAllowed/recordRejected are deliberately fire-and-forget (see
        // the class Javadoc) — give the detached INCR a moment to land
        // before asserting on it, the same way a real dashboard read would
        // race a recent request.
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- A: allowed counter increments ----

    @Test
    void recordAllowed_incrementsTheAllowedKey_underTheNewNamespace() {
        recorder.recordAllowed("CATALOG:FREE");
        awaitWrite();

        String value = redisTemplate.opsForValue().get("gateway:admin:rate-limit-activity:CATALOG:FREE:allowed").block();
        assertThat(value).isEqualTo("1");
    }

    @Test
    void recordAllowed_calledTwice_accumulates() {
        recorder.recordAllowed("CATALOG:FREE");
        recorder.recordAllowed("CATALOG:FREE");
        awaitWrite();

        String value = redisTemplate.opsForValue().get("gateway:admin:rate-limit-activity:CATALOG:FREE:allowed").block();
        assertThat(value).isEqualTo("2");
    }

    // ---- B: rejected counter increments ----

    @Test
    void recordRejected_incrementsTheRejectedKey_underTheNewNamespace() {
        recorder.recordRejected("AUTH:PUBLIC");
        awaitWrite();

        String value = redisTemplate.opsForValue().get("gateway:admin:rate-limit-activity:AUTH:PUBLIC:rejected").block();
        assertThat(value).isEqualTo("1");
    }

    // ---- Redis keys use the new namespace; RedisRateLimiter's internal keys are never touched ----

    @Test
    void theRecorder_neverWritesOrReads_redisRateLimitersOwnInternalKeys() {
        recorder.recordAllowed("BOOKING:PRO");
        recorder.recordRejected("BOOKING:PRO");
        awaitWrite();

        Set<String> keys = redisTemplate.keys("*").collect(java.util.stream.Collectors.toSet()).block();

        assertThat(keys).isNotNull();
        assertThat(keys).allSatisfy(key ->
                assertThat(key).as("key '%s'", key).doesNotContain("request_rate_limiter"));
        assertThat(keys).anyMatch(key -> key.startsWith("gateway:admin:rate-limit-activity:"));
    }

    // ---- C: activity is returned correctly; multiple policy IDs are represented correctly ----

    @Test
    void readActivitySection_multiplePolicyIds_returnsEachOnesCountsIndependently() {
        recorder.recordAllowed("CATALOG:FREE");
        recorder.recordAllowed("CATALOG:FREE");
        recorder.recordRejected("CATALOG:FREE");
        recorder.recordAllowed("AUTH:PUBLIC");
        // BOOKING:PRO deliberately untouched — must still appear, at 0/0.
        awaitWrite();

        RateLimitActivitySection section = recorder
                .readActivitySection(List.of("CATALOG:FREE", "AUTH:PUBLIC", "BOOKING:PRO"))
                .block(Duration.ofSeconds(5));

        assertThat(section).isNotNull();
        assertThat(section.redisAvailable()).isTrue();
        assertThat(section.byPolicy()).containsEntry("CATALOG:FREE", new RateLimitActivityDto(2, 1));
        assertThat(section.byPolicy()).containsEntry("AUTH:PUBLIC", new RateLimitActivityDto(1, 0));
        assertThat(section.byPolicy()).containsEntry("BOOKING:PRO", new RateLimitActivityDto(0, 0));
    }
}
