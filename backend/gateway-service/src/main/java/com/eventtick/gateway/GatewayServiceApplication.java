package com.eventtick.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick API Gateway.
 *
 * <p>Phase 7.1: basic path-based routing to the three backend services,
 * configured in {@code application.yml}, plus (Phase 7.2) CORS for the
 * browser frontend, and (Phase 7.3) JWT authentication of protected
 * requests (see {@code config/GatewaySecurityConfig}). No rate limiting yet
 * — that is added in a later phase (see
 * {@code docs/architecture.md}, section "API Gateway").
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
