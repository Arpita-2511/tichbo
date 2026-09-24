package com.eventtick.gateway.exception;

import com.eventtick.gateway.filter.RequestIdWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    @Test
    void statusChosenBySpring_isKept() {
        assertThat(GatewayErrorHandler.statusFor(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(GatewayErrorHandler.statusFor(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GatewayErrorHandler.statusFor(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownStatusCode_fallsBackToTheRightClass() {
        assertThat(GatewayErrorHandler.statusFor(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(499))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(GatewayErrorHandler.statusFor(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(599))))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void refusedOrUnresolvableUpstream_is503_evenWhenWrapped() {
        assertThat(GatewayErrorHandler.statusFor(new ConnectException("Connection refused: /10.0.0.5:8081")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(GatewayErrorHandler.statusFor(new RuntimeException("wrapper", new UnknownHostException("user-service"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeouts_are504() {
        // A connect timeout is a ConnectException subclass; it must win over the 503 rule.
        assertThat(GatewayErrorHandler.statusFor(new ConnectTimeoutException("connect timed out")))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GatewayErrorHandler.statusFor(new RuntimeException(ReadTimeoutException.INSTANCE)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GatewayErrorHandler.statusFor(new java.util.concurrent.TimeoutException()))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void aBrokenUpstreamConnection_is502() {
        assertThat(GatewayErrorHandler.statusFor(new IOException("Connection reset by peer")))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void anythingElse_is500() {
        assertThat(GatewayErrorHandler.statusFor(new IllegalStateException("boom"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(GatewayErrorHandler.statusFor(new NullPointerException())).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void aSelfReferentialCauseChain_doesNotLoopForever() {
        Exception loop = new Exception("a");
        Exception other = new Exception("b", loop);
        loop.initCause(other);

        assertThat(GatewayErrorHandler.statusFor(loop)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void clientMessages_areFixedText_neverTheExceptionText() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED,
                HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT}) {
            assertThat(GatewayErrorHandler.messageFor(status)).isNotBlank().doesNotContain("Exception");
        }
    }

    @Test
    void handlerWritesTheStandardBody_withoutTheExceptionText() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        exchange.getAttributes().put(RequestIdWebFilter.ATTRIBUTE, "id-under-test");
        GatewayErrorHandler handler = new GatewayErrorHandler(new GatewayErrorWriter(new ObjectMapper()));

        handler.handle(exchange, new ConnectException("Connection refused: /10.9.8.7:8081")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"requestId\":\"id-under-test\"").contains("SERVICE_UNAVAILABLE")
                .doesNotContain("10.9.8.7").doesNotContain("refused");
    }
}
