package com.eventtick.payment.exception;

/**
 * Thrown when {@code booking-service} itself cannot be reached or returns
 * an unexpected error — distinct from {@link BookingNotFoundException}
 * (booking-service responded, the booking just doesn't exist). Mapped to
 * a 5xx, not a 4xx: this is an operational failure of a dependency, not a
 * client mistake.
 */
public class BookingServiceUnavailableException extends RuntimeException {

    public BookingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
