package com.eventtick.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * The single 401 the gateway returns for a missing, malformed, expired,
 * wrongly-signed, or otherwise invalid token. Same JSON shape as every
 * other service's errors ({@code status/error/message/timestamp}) and
 * identical to user-service's own 401, so the frontend handles one format.
 *
 * <p>Deliberately says nothing about <i>why</i> — no "token expired", no
 * signature details, no echo of the token. It also replaces Spring's
 * default {@code WWW-Authenticate: Bearer error="invalid_token",
 * error_description="..."} header, which would leak that reason; only a bare
 * {@code Bearer} challenge is sent (RFC 6750 asks for one on a 401).
 */
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private record ErrorBody(int status, String error, String message, String timestamp) {
    }

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new ErrorBody(
                    HttpStatus.UNAUTHORIZED.value(),
                    "UNAUTHENTICATED",
                    "Authentication is required and either missing or invalid.",
                    Instant.now().toString()));
        } catch (JsonProcessingException e) {
            return response.setComplete(); // status + headers still convey the 401
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
