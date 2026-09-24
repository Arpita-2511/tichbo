package com.eventtick.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request through the gateway a correlation id, carried in the
 * {@value #HEADER} header:
 *
 * <ul>
 *   <li>an incoming id is kept (and forwarded upstream unchanged) if it looks
 *   safe; otherwise, or if there is none, a random UUID is generated;
 *   <li>the id is put on the request forwarded to the upstream service, so
 *   that service's own logs can be matched to the gateway's;
 *   <li>the same id is on the response, including the gateway's own 401/404/5xx
 *   responses.
 * </ul>
 *
 * <p><b>Why a {@code WebFilter} and not a Spring Cloud Gateway
 * {@code GlobalFilter}:</b> a {@code GlobalFilter} only runs for requests that
 * matched a route, but the 401s (Spring Security) and 404s (no route) are
 * produced <i>before</i> any route runs — and those are exactly the responses
 * a caller most needs an id for. This runs first of all, ahead of Spring
 * Security.
 *
 * <p><b>The incoming value is untrusted.</b> It ends up in log lines and in
 * headers sent to other services, so it is only accepted if it is 1–128 characters
 * of letters, digits, {@code . _ : -} (which covers UUIDs and the common
 * trace-id formats). Anything else is replaced by a generated id rather than
 * rejected — a bad id should never fail a request. The id is a random UUID:
 * it carries no user, token, or time information.
 *
 * <p>Also writes one log line per request with only safe metadata — id,
 * method, path (never the query string, which may hold secrets), status and
 * duration. Headers, and so Authorization tokens, are never logged.
 */
@Component
public class RequestIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Request-ID";

    /** Exchange attribute holding the id, for error handlers that build a response body. */
    public static final String ATTRIBUTE = RequestIdWebFilter.class.getName() + ".REQUEST_ID";

    private static final Pattern ACCEPTABLE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private static final Logger log = LoggerFactory.getLogger(RequestIdWebFilter.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();
        String requestId = resolve(original.getHeaders().getFirst(HEADER));
        long startedAt = System.nanoTime();

        // set(), not add(): a request that arrived with several X-Request-ID
        // headers goes upstream with exactly one.
        ServerHttpRequest forwarded = original.mutate().headers(h -> h.set(HEADER, requestId)).build();
        exchange.getAttributes().put(ATTRIBUTE, requestId);

        // At commit time, so it also covers responses written by security
        // filters and error handlers, and so it replaces any X-Request-ID an
        // upstream service put on its response (one value, ours).
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(HEADER, requestId);
            HttpStatusCode status = response.getStatusCode();
            log.info("request id={} method={} path={} status={} durationMs={}",
                    requestId, original.getMethod(), original.getPath().value(),
                    status == null ? "-" : status.value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(forwarded).build());
    }

    /** The id of this request, or {@code null} if the filter has not run (e.g. in a unit test). */
    public static String requestId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }

    static String resolve(String incoming) {
        if (incoming != null && ACCEPTABLE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
