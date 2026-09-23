package com.eventtick.booking.exception;

import com.eventtick.booking.entity.BookingStatus;

import java.util.UUID;

/**
 * Thrown when an operation is attempted on a {@code Booking} whose current
 * {@link BookingStatus} doesn't allow it (e.g. confirming a booking that
 * isn't {@code PENDING}).
 */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(UUID bookingId, BookingStatus currentStatus, String attemptedOperation) {
        super("Booking " + bookingId + " is in status " + currentStatus
                + " and cannot be " + attemptedOperation + ".");
    }
}
