package com.eventtick.user.exception;

import com.eventtick.user.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Maps the User Service's exceptions (and standard Spring/bean-validation
 * failures) to the {@link ErrorResponse} shape. Structurally consistent
 * with booking-service's and catalog-service's {@code GlobalExceptionHandler}
 * — separate classes, no shared code between services.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    /** FR-37: changing a user's plan to one that doesn't exist. */
    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlanNotFound(PlanNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", ex.getMessage());
    }

    /**
     * FR-37: an administrator tried to change their own role. The actor is
     * generally authorized to reach this endpoint (a real ADMIN token) —
     * this is a targeted business-rule conflict on this specific request,
     * not a role/authorization failure, so it maps to 409 like
     * booking-service's {@code InvalidBookingStateException} rather than
     * 403 (which this project reserves for "authenticated but lacking the
     * required role," a Spring-Security-level concept).
     */
    @ExceptionHandler(SelfRoleModificationException.class)
    public ResponseEntity<ErrorResponse> handleSelfRoleModification(SelfRoleModificationException ex) {
        return respond(HttpStatus.CONFLICT, "SELF_ROLE_MODIFICATION_NOT_ALLOWED", ex.getMessage());
    }

    /**
     * A race between the registration pre-check and the database's own
     * {@code uq_users_email} constraint (two concurrent registrations for
     * the same email). The pre-check in {@code AuthService} handles the
     * common case with a clean {@link DuplicateEmailException}; this is
     * the fallback for the race the pre-check can't close on its own —
     * same reasoning as catalog-service's identical handler.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT",
                "The request conflicts with an existing record and cannot be completed.");
    }

    /** From failed {@code @Valid} bean validation on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** A path/query value that isn't a well-formed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /**
     * FR-37: a request body that Jackson can't parse — most notably a
     * {@code role} value outside the {@code UserRole} enum (e.g.
     * {@code "SUPERADMIN"}), which fails during JSON deserialization,
     * before {@code @Valid} bean validation ever runs. Also covers plain
     * malformed JSON. Kept generic (no raw Jackson message passed through)
     * to avoid leaking internal type/field details to the client.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequestBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request body is malformed or contains an invalid value.");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), errorCode, message));
    }
}
