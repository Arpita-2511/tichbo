package com.eventtick.booking.exception;

import java.util.UUID;

/**
 * Thrown when a {@code ShowSeat} passed to an operation doesn't actually
 * belong to the {@code showId} the operation was given.
 *
 * <p>The database does not enforce this invariant (see the table comment
 * on {@code bookings} in
 * {@code database/migrations/0009_create_bookings_table.up.sql}) — it is
 * checked here, in the service layer, instead.
 */
public class SeatShowMismatchException extends RuntimeException {

    public SeatShowMismatchException(UUID showSeatId, UUID expectedShowId, UUID actualShowId) {
        super("ShowSeat " + showSeatId + " belongs to show " + actualShowId
                + ", not the requested show " + expectedShowId + ".");
    }
}
