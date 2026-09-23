package com.eventtick.booking.exception;

import com.eventtick.booking.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Maps {@code BookingService}'s exceptions (and standard Spring request
 * validation failures) to the {@link ErrorResponse} shape from the
 * approved API contract. Centralized here rather than handled per
 * controller method, so {@code BookingController} stays a thin delegator.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(BookingNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShowSeatNotFound(EntityNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "SHOW_SEAT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidSeatStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSeatState(InvalidSeatStateException ex) {
        return respond(HttpStatus.CONFLICT, "INVALID_SEAT_STATE", ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingState(InvalidBookingStateException ex) {
        return respond(HttpStatus.CONFLICT, "INVALID_BOOKING_STATE", ex.getMessage());
    }

    @ExceptionHandler(SeatShowMismatchException.class)
    public ResponseEntity<ErrorResponse> handleSeatShowMismatch(SeatShowMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "SEAT_SHOW_MISMATCH", ex.getMessage());
    }

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

    /** A path/query value (e.g. {@code showId}) that isn't a well-formed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for '" + ex.getName() + "': " + ex.getValue();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** A required query parameter (e.g. {@code userId} on the list-bookings endpoint) is missing. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), errorCode, message));
    }
}
