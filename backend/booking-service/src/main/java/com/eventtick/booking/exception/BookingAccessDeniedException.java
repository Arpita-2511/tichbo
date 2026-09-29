package com.eventtick.booking.exception;

import java.util.UUID;

/**
 * The caller is authenticated but is not allowed to act on this booking
 * (BR-07). Distinct from {@link BookingNotFoundException}: the booking exists.
 */
public class BookingAccessDeniedException extends RuntimeException {

    public BookingAccessDeniedException(UUID bookingId) {
        super("You do not have access to booking " + bookingId + ".");
    }

    public BookingAccessDeniedException(String message) {
        super(message);
    }
}
