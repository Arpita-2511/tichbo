package com.eventtick.gateway.security;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import com.eventtick.gateway.filter.RequestIdWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing in the gateway produces a 403 yet (authentication only), so the
 * handler is checked directly: when a role rule is added later, its rejection
 * already has the standard body.
 */
class JsonAccessDeniedHandlerTest {

    @Test
    void writesAStandard403_withTheRequestId_andNoDetailsOfWhyItWasDenied() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/bookings").build());
        exchange.getAttributes().put(RequestIdWebFilter.ATTRIBUTE, "id-for-a-403");
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(new GatewayErrorWriter(new ObjectMapper()));

        handler.handle(exchange, new AccessDeniedException("requires ROLE_ADMIN")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNotNull().asString().startsWith("application/json");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
                .contains("\"status\":403")
                .contains("\"error\":\"FORBIDDEN\"")
                .contains("\"requestId\":\"id-for-a-403\"")
                .doesNotContain("ROLE_ADMIN")
                .doesNotContain("AccessDeniedException");
    }
}
