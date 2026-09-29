package com.eventtick.payment.exception;

import java.util.UUID;

/** Thrown when a {@code bookingId} does not resolve to any existing booking (per booking-service). */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }
}
