package com.eventtick.booking.exception;

import com.eventtick.booking.entity.ShowSeatStatus;

import java.util.UUID;

/**
 * Thrown when a {@code ShowSeat} is not in the status a requested
 * transition requires (e.g. holding a seat that isn't {@code AVAILABLE},
 * or confirming a booking whose seat is no longer {@code HELD}).
 *
 * <p>This is the exception a caller sees when the row-locking check in
 * {@code BookingService} loses a race — see that class's Javadoc for the
 * concurrency scenario this guards against.
 */
public class InvalidSeatStateException extends RuntimeException {

    public InvalidSeatStateException(UUID showSeatId, ShowSeatStatus expected, ShowSeatStatus actual) {
        super("ShowSeat " + showSeatId + " must be " + expected + " but was " + actual + ".");
    }
}
