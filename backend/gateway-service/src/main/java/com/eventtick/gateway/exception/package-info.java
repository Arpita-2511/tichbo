/**
 * Error handling for the API Gateway (Phase 7.4): one JSON error shape for
 * everything the gateway itself rejects or fails at — see
 * {@link com.eventtick.gateway.exception.GatewayErrorWriter} (the shape) and
 * {@link com.eventtick.gateway.exception.GatewayErrorHandler} (unknown routes
 * and upstream failures). Authentication failures use the same writer from
 * {@code com.eventtick.gateway.security}.
 */
package com.eventtick.gateway.exception;
