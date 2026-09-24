/**
 * JWT authentication at the gateway (Phase 7.3): validating the tokens
 * issued by user-service, turning them into an authentication, and the
 * single generic 401 response. The filter chains that use these are in
 * {@code com.eventtick.gateway.config.GatewaySecurityConfig}.
 */
package com.eventtick.gateway.security;
