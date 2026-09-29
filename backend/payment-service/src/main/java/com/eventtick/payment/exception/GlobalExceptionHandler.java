package com.eventtick.payment.exception;

import com.eventtick.payment.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Maps {@code PaymentService}'s and {@code BookingServiceClient}'s
 * exceptions (and standard Spring request validation failures) to the
 * {@link ErrorResponse} shape every service in this project uses.
 * Centralized here rather than handled per controller method, matching
 * booking-service's own {@code GlobalExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(BookingNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PaymentOwnershipException.class)
    public ResponseEntity<ErrorResponse> handlePaymentOwnership(PaymentOwnershipException ex) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return respond(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ErrorResponse> handlePaymentConflict(PaymentConflictException ex) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_FOR_BOOKING", ex.getMessage());
    }

    /**
     * booking-service unreachable/erroring — an operational failure of a
     * dependency, not a client mistake. The underlying cause (which could
     * include an internal host/port) is logged by
     * {@code BookingServiceClient} itself, never put in the response body.
     */
    @ExceptionHandler(BookingServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleBookingServiceUnavailable(BookingServiceUnavailableException ex) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_SERVICE_UNAVAILABLE",
                "The booking service is temporarily unavailable. Please try again shortly.");
    }

    /** From failed {@code @Valid} bean validation on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** A path/query value (e.g. {@code paymentId}, {@code bookingId}) that isn't a well-formed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** The {@code bookingId} query parameter is missing on the booking-scoped lookup. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), errorCode, message));
    }
}
