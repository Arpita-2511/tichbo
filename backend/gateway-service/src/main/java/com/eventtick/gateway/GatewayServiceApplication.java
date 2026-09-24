package com.eventtick.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick API Gateway.
 *
 * <p>Phase 7.1: basic path-based routing to the three backend services,
 * configured in {@code application.yml}. No filters, authentication, or
 * rate limiting yet — those are added in later phases (see
 * {@code docs/architecture.md}, section "API Gateway").
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
