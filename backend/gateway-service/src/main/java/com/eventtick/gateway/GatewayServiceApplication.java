package com.eventtick.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick API Gateway.
 *
 * <p>This is a bootable skeleton only. No routes, filters, authentication,
 * or rate limiting are configured yet — those are added in later phases
 * (see {@code docs/architecture.md}, section "API Gateway").
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
