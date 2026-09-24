package com.eventtick.user.dto;

import java.time.Instant;

/**
 * Uniform error body returned by {@code GlobalExceptionHandler}. Mirrors
 * the shape used by booking-service's and catalog-service's
 * {@code ErrorResponse} — a separate class (independent Maven module, no
 * shared code between services), kept structurally identical so every
 * service's error responses look the same to a client calling all three.
 */
public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
