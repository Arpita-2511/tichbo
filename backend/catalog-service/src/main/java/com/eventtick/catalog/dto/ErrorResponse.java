package com.eventtick.catalog.dto;

import java.time.Instant;

/**
 * Uniform error body returned by {@code GlobalExceptionHandler}. Mirrors
 * the shape used by booking-service's {@code ErrorResponse} — the two
 * are separate classes (independent Maven modules, no shared code
 * between services), kept structurally identical so the two services'
 * error responses look the same to any client calling both.
 */
public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
