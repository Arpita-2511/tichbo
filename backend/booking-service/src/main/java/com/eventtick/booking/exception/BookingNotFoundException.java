package com.eventtick.booking.exception;

import java.util.UUID;

/** Thrown when a {@code bookingId} does not correspond to any existing booking. */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }
}
