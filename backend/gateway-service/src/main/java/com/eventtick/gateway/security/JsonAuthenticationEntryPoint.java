package com.eventtick.gateway.security;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The single 401 the gateway returns for a missing, malformed, expired,
 * wrongly-signed, or otherwise invalid token. Same JSON shape as every
 * other gateway error (written by {@link GatewayErrorWriter}, so it also
 * carries the request id) and the same fields as user-service's own 401, so
 * the frontend handles one format.
 *
 * <p>Deliberately says nothing about <i>why</i> — no "token expired", no
 * signature details, no echo of the token. It also replaces Spring's
 * default {@code WWW-Authenticate: Bearer error="invalid_token",
 * error_description="..."} header, which would leak that reason; only a bare
 * {@code Bearer} challenge is sent (RFC 6750 asks for one on a 401).
 */
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final GatewayErrorWriter errors;

    public JsonAuthenticationEntryPoint(GatewayErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return errors.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required and either missing or invalid.");
    }
}
