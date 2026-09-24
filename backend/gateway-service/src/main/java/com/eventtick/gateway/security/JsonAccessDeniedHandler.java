package com.eventtick.gateway.security;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The 403 for a request that is authenticated but not allowed. The gateway
 * has no role rules yet (see {@code GatewaySecurityConfig}), so nothing
 * produces a 403 today; this exists so that when a rule is added its
 * rejection already has the gateway's standard body instead of Spring's empty
 * one.
 */
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final GatewayErrorWriter errors;

    public JsonAccessDeniedHandler(GatewayErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.");
    }
}
