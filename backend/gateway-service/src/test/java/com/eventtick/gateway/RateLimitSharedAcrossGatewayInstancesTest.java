package com.eventtick.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11/12: proves the rate-limit bucket lives in Redis, not in this
 * process's memory. It actually runs <b>two</b> separate gateway instances —
 * two full Spring contexts, two embedded Netty servers on two random ports —
 * against the same embedded {@link TestRedis} and the same
 * {@link StubUpstream}, the way "gateway instance A / instance B / instance
 * C, all pointed at one Redis" looks in the real architecture.
 *
 * <p>If the bucket were an in-memory counter inside
 * {@code RateLimitingGlobalFilter}, each instance would keep its own
 * allowance and neither would ever see the other's usage. Because it is
 * instead a key in Redis, spending it through instance A must also block the
 * very next request made to instance B for that same identity — that is
 * exactly what this test drives and asserts.
 *
 * <p>The policy is configured so one request costs the <i>entire</i> bucket
 * ({@code requested-tokens == burst-capacity}), rather than draining it one
 * token per call: the token bucket's refill is measured against Redis's own
 * wall clock, so with two full Spring contexts (this test's actual subject)
 * competing for CPU/GC on a shared machine, a refill margin of only
 * "replenish-rate's one-second granularity" would occasionally let a stray
 * token slip in between calls that are supposed to be back to back. Needing
 * the whole bucket to refill (several seconds at this policy) instead of
 * just one token makes the test robust to that jitter without weakening what
 * it proves.
 */
class RateLimitSharedAcrossGatewayInstancesTest {

    private static final int BURST_CAPACITY = 5;

    private final StubUpstream stub = new StubUpstream();
    private final TestRedis redis = TestRedis.start();
    private ConfigurableApplicationContext instanceA;
    private ConfigurableApplicationContext instanceB;

    @AfterEach
    void cleanup() {
        if (instanceA != null) {
            instanceA.close();
        }
        if (instanceB != null) {
            instanceB.close();
        }
        stub.stop();
        redis.stop();
    }

    private ConfigurableApplicationContext startGatewayInstance() {
        // GET /api/users/me is the USER category; the tokens below default to
        // plan=Free (TestTokens' own default), so USER.FREE is the policy that
        // actually applies — override just that one cell.
        List<String> props = new ArrayList<>(List.of(
                "server.port=0",
                "spring.data.redis.host=" + redis.host(),
                "spring.data.redis.port=" + redis.port(),
                "eventtick.rate-limit.policies.USER.FREE.replenish-rate=1",
                "eventtick.rate-limit.policies.USER.FREE.burst-capacity=" + BURST_CAPACITY,
                // Equal to burst-capacity: one call drains the whole bucket (see
                // the class Javadoc for why).
                "eventtick.rate-limit.policies.USER.FREE.requested-tokens=" + BURST_CAPACITY,
                "jwt.secret=" + TestTokens.SECRET,
                "jwt.issuer=" + TestTokens.ISSUER,
                "jwt.audience=" + TestTokens.AUDIENCE));
        String[][] routes = {
                {"user-service", "/api/auth/**,/api/users/**"},
                {"catalog-service", "/api/catalog/**"},
                {"booking-service", "/api/bookings/**"},
        };
        for (int i = 0; i < routes.length; i++) {
            props.add("spring.cloud.gateway.routes[" + i + "].id=" + routes[i][0]);
            props.add("spring.cloud.gateway.routes[" + i + "].uri=" + stub.url());
            props.add("spring.cloud.gateway.routes[" + i + "].predicates[0]=Path=" + routes[i][1]);
        }

        // Command-line arguments, not .properties(...): SpringApplicationBuilder
        // .properties() adds "default properties" at the LOWEST priority, so
        // application.yml's own server.port: 8080 would still win and both
        // instances would fight over the same port. Command-line arguments
        // outrank application.yml, which is what's needed to actually
        // override it down to a random port per instance.
        String[] args = props.stream().map(p -> "--" + p).toArray(String[]::new);
        return new SpringApplicationBuilder(GatewayServiceApplication.class)
                .web(WebApplicationType.REACTIVE)
                // Publishes "local.server.port" once the embedded server has
                // actually bound its (random) port, since this isn't booted
                // through @SpringBootTest(webEnvironment = RANDOM_PORT), which
                // would register this automatically.
                .initializers(new ServerPortInfoApplicationContextInitializer())
                .run(args);
    }

    private WebTestClient clientFor(ConfigurableApplicationContext context) {
        String port = context.getEnvironment().getProperty("local.server.port");
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void exhaustingTheBucketOnOneInstance_blocksTheNextRequestOnAnotherInstance() {
        instanceA = startGatewayInstance();
        instanceB = startGatewayInstance();
        WebTestClient clientA = clientFor(instanceA);
        WebTestClient clientB = clientFor(instanceB);

        // Warm each instance's whole pipeline once, under a throwaway identity,
        // before the timed sequence below. Otherwise either instance's very
        // first request (routing, security, the rate limiter's first EVAL of
        // its Lua script, the upstream call — all not yet JIT'd) can itself
        // take longer than replenish-rate's one-second granularity, letting a
        // stray token refill between two calls meant to be back to back.
        String warmup = "Bearer " + TestTokens.valid().build();
        clientA.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, warmup).exchange();
        clientB.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, warmup).exchange();
        instanceA.getBean(org.springframework.data.redis.connection.ReactiveRedisConnectionFactory.class)
                .getReactiveConnection().serverCommands().flushAll().block();
        stub.clear();

        // One identity, called against both instances — never told about each
        // other except through the Redis they share.
        String auth = "Bearer " + TestTokens.valid().build();

        // One call drains the entire bucket for this identity (requested-tokens
        // == burst-capacity, above) — via instance A.
        clientA.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isOk();

        // Instance B, a different process-level context that was never told
        // about instance A's request directly, must see that SAME identity as
        // already exhausted — the only way that's possible is if both read
        // and write the same key in the same Redis.
        clientB.get().uri("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth)
                .exchange().expectStatus().isEqualTo(429);

        // Instance B's rejected call never reached the upstream either.
        assertThat(stub.received()).hasSize(1);
    }
}
