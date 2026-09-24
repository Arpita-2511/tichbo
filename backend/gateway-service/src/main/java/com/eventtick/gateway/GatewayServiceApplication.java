package com.eventtick.gateway;

import com.eventtick.gateway.ratelimit.RateLimitPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Eventtick API Gateway.
 *
 * <p>Phase 7.1: basic path-based routing to the three backend services,
 * configured in {@code application.yml}, plus (Phase 7.2) CORS for the
 * browser frontend, (Phase 7.3) JWT authentication of protected requests
 * (see {@code config/GatewaySecurityConfig}), (Phase 7.4) request
 * correlation and controlled errors, (Phase 7.5) upstream timeouts, and
 * (Phase 11/12) Redis-backed dynamic rate limiting (see
 * {@code ratelimit.RateLimitingGlobalFilter}). See
 * {@code docs/architecture.md}, section "API Gateway", for the full picture.
 */
@SpringBootApplication
@EnableConfigurationProperties(RateLimitPolicyProperties.class)
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
