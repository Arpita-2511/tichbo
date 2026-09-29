package com.eventtick.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 14 Step 1: service-local health and Prometheus metrics endpoints.
 * Reachable without an Eventtick JWT (see {@code SecurityConfig}'s narrow
 * allowlist), while every other Actuator endpoint stays unreachable — both
 * because {@code management.endpoints.web.exposure.include} only turns on
 * {@code health} and {@code prometheus} (so Actuator itself never registers
 * a handler for the others), and because anything not on the allowlist still
 * falls through to {@code SecurityConfig}'s {@code anyRequest().authenticated()}.
 * Existing customer/auth endpoints are covered by {@link CorsIntegrationTest}
 * and {@link AuthenticationIntegrationTest}; this class does not repeat them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// Spring Boot Test disables every metrics exporter except "simple" inside a
// @SpringBootTest context by default (see the framework's own
// AutoConfigureObservability Javadoc) — without this, /actuator/prometheus
// would 404 here even though it works in the real running application.
@AutoConfigureObservability
class ActuatorEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_isReachableWithoutAToken_andUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void liveness_isReachableWithoutAToken_andUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void readiness_isReachableWithoutAToken_andUp() throws Exception {
        // The test datasource (H2, see src/test/resources/application.yml) is
        // reachable, so the auto-configured DataSourceHealthIndicator reports
        // UP under normal local conditions, the same way a real PostgreSQL
        // connection would.
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void prometheus_isReachableWithoutAToken_andExposesStandardMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("process_uptime_seconds")));
    }

    @Test
    void unauthenticated_sensitiveActuatorEndpoints_areNotExposed() throws Exception {
        // Not on SecurityConfig's allowlist, so this is blocked by
        // anyRequest().authenticated() before Actuator (which never
        // registered a handler for these anyway, since they aren't in
        // management.endpoints.web.exposure.include) is even reached.
        for (String path : new String[]{
                "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/loggers", "/actuator/heapdump"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void businessEndpoints_stillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
}
