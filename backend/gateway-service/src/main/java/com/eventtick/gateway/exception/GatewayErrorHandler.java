package com.eventtick.gateway.exception;

import com.eventtick.gateway.filter.RequestIdWebFilter;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Turns everything that goes wrong <i>inside</i> the gateway — no matching
 * route, an unreachable or misbehaving upstream service, a malformed request —
 * into the same clean JSON error as the security layer's 401, instead of Spring
 * Boot's default error page/JSON.
 *
 * <p>Being an {@link ErrorWebExceptionHandler} bean makes Boot's default
 * handler ({@link ErrorWebFluxAutoConfiguration}) back off, and the order puts
 * this ahead of the other exception handlers.
 *
 * <p>Status mapping (the original status is kept whenever the gateway itself
 * already chose one, e.g. 404 or 504):
 * <ul>
 *   <li>no route, bad request, etc. → the status Spring chose (404, 400, 405, ...);
 *   <li>upstream refused/unreachable (connect error, unknown host) → 503;
 *   <li>upstream too slow (connect/read timeout) → 504;
 *   <li>upstream broke the connection or sent garbage (other I/O error) → 502;
 *   <li>anything unexpected → 500.
 * </ul>
 *
 * <p>The client always gets a fixed message for the status. The exception itself
 * — which for a refused connection would include the internal host and port —
 * is never sent to the client; only its class name is logged, with the request id.
 * A response the upstream has already started sending cannot be replaced, so in
 * that case the original error is rethrown for the framework to handle.
 */
@Component
@Order(-2) // Boot's own handler is -1
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final GatewayErrorWriter errors;

    public GatewayErrorHandler(GatewayErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = statusFor(ex);
        if (status.is5xxServerError()) {
            log.warn("gateway error id={} status={} cause={}",
                    RequestIdWebFilter.requestId(exchange), status.value(), rootCause(ex).getClass().getSimpleName());
        }
        return errors.write(exchange, status, status.name(), messageFor(status));
    }

    static HttpStatus statusFor(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status != null) {
                return status;
            }
            return rse.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // Walk the cause chain (bounded): reactor-netty wraps the real error.
        Throwable t = ex;
        for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
            if (t instanceof ResponseStatusException) {
                return statusFor(t);
            }
            if (t instanceof ConnectTimeoutException || t instanceof TimeoutException
                    || t instanceof java.util.concurrent.TimeoutException || t instanceof SocketTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof NoRouteToHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (t instanceof IOException) {
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED -> "The request method is not allowed for this resource.";
            case BAD_GATEWAY -> "The upstream service returned an invalid response.";
            case SERVICE_UNAVAILABLE -> "The requested service is temporarily unavailable.";
            case GATEWAY_TIMEOUT -> "The requested service did not respond in time.";
            default -> status.is4xxClientError()
                    ? "The request could not be processed."
                    : "The gateway could not complete the request.";
        };
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable t = ex;
        for (int depth = 0; t.getCause() != null && depth < 10; depth++) {
            t = t.getCause();
        }
        return t;
    }
}
