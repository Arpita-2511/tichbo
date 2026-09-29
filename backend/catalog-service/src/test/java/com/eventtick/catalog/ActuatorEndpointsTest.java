package com.eventtick.catalog;

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
 * catalog-service has no Spring Security dependency at all (every endpoint,
 * including the {@code /api/admin/**} ones, is open at this layer — role
 * enforcement is the Gateway's job, see {@code GatewaySecurityConfig}), so
 * unlike user-service/gateway-service there is no allowlist to add here:
 * Actuator is reachable the same way every other endpoint already is. Only
 * {@code management.endpoints.web.exposure.include} decides which Actuator
 * endpoints exist at all.
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
    void health_isReachable_andUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void liveness_isReachable_andUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void readiness_isReachable_andUp() throws Exception {
        // The test datasource (H2, see src/test/resources/application.yml) is
        // reachable, so the auto-configured DataSourceHealthIndicator reports
        // UP under normal local conditions, the same way a real PostgreSQL
        // connection would.
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void prometheus_isReachable_andExposesStandardMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("process_uptime_seconds")));
    }

    @Test
    void sensitiveActuatorEndpoints_areNotExposed() throws Exception {
        for (String path : new String[]{
                "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/mappings", "/actuator/loggers", "/actuator/heapdump"}) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
