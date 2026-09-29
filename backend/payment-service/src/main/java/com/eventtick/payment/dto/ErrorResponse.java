package com.eventtick.payment.dto;

import java.time.Instant;

/** Uniform error body returned by {@code GlobalExceptionHandler} — same shape every service in this project uses. */
public record ErrorResponse(int status, String error, String message, Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
