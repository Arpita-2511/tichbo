/**
 * Spring configuration for the API Gateway.
 *
 * <p>Holds the security filter chains (Phase 7.3, {@code GatewaySecurityConfig}).
 * CORS and route definitions live in {@code application.yml}; rate-limit
 * wiring will be added here in a later phase.
 */
package com.eventtick.gateway.config;
