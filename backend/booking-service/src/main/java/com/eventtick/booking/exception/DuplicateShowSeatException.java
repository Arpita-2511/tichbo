package com.eventtick.booking.exception;

import java.util.List;
import java.util.UUID;

/**
 * Thrown when creating show_seats inventory would duplicate an existing
 * (show, seat) pairing already persisted for this show — the request-shape
 * duplicate (the same seatId repeated within one request) is instead an
 * {@link IllegalArgumentException}, mirroring
 * {@code BookingService.requireNonEmpty}'s existing "Duplicate seat ids in
 * request" check. Distinct from {@link InvalidSeatStateException}: no
 * existing {@code ShowSeat} row is being transitioned here — this is a
 * request that would violate {@code uq_show_seats_show_seat}
 * (migration 0008) before any new row is even written.
 */
public class DuplicateShowSeatException extends RuntimeException {

    public DuplicateShowSeatException(UUID showId, List<UUID> duplicateSeatIds) {
        super("Show " + showId + " already has inventory for seat id(s): " + duplicateSeatIds + ".");
    }
}
