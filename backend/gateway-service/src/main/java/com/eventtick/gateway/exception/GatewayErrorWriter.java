package com.eventtick.gateway.exception;

import com.eventtick.gateway.filter.RequestIdWebFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * The one place a gateway-generated error response is written, so a 401 from
 * the security layer, a 403, a 404 for an unknown route and a 5xx for a failing
 * upstream all have the same shape:
 *
 * <pre>{"status":404,"error":"NOT_FOUND","message":"...","requestId":"...","timestamp":"..."}</pre>
 *
 * The first four fields match what user-service and the other services
 * return, plus {@code requestId} so a client can quote it in a bug report.
 *
 * <p>Callers pass a fixed, human-written message; nothing about the actual
 * failure (exception text, class names, hosts, paths) is ever put in the body.
 * CORS headers, when the request had an allowed Origin, are added earlier by
 * the security layer's CORS filter and are left untouched here; the
 * {@code X-Request-ID} response header is added by {@link RequestIdWebFilter}.
 */
@Component
public class GatewayErrorWriter {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ErrorBody(int status, String error, String message, String requestId, String timestamp) {
    }

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new ErrorBody(
                    status.value(), errorCode, message,
                    RequestIdWebFilter.requestId(exchange), Instant.now().toString()));
        } catch (JsonProcessingException e) {
            return response.setComplete(); // status and headers still convey the error
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
