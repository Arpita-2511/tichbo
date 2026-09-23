package com.eventtick.catalog.exception;

import com.eventtick.catalog.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Maps the Catalog Service's exceptions (and standard Spring request
 * validation failures) to the {@link ErrorResponse} shape. Centralized
 * here rather than handled per controller method, mirroring
 * booking-service's {@code GlobalExceptionHandler} — a separate class in
 * a separate module, kept structurally consistent rather than shared.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CatalogEntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CatalogEntityNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", ex.getMessage());
    }

    /**
     * A database-level FK/constraint rejection (e.g. deleting a Content
     * still referenced by a Show via {@code fk_shows_content}, or any
     * other {@code ON DELETE RESTRICT}/unique-constraint violation).
     * PostgreSQL, via {@code fk_shows_content} and similar constraints,
     * remains the sole source of truth for these relationships — this
     * handler only translates the resulting
     * {@link DataIntegrityViolationException} into a client-safe
     * response; it does not pre-check or duplicate that logic. The raw
     * exception message (SQL state, constraint name, statement) is
     * deliberately not included in the response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT",
                "The request conflicts with an existing related record and cannot be completed.");
    }

    /** Service-layer validation (blank fields, non-positive numbers, invalid time ranges). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    /** From failed {@code @Valid} bean validation on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** A path/query value (e.g. {@code id}, {@code venueId}) that isn't a well-formed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), errorCode, message));
    }
}
