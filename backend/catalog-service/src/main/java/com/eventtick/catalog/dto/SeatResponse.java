package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.Seat;
import com.eventtick.catalog.entity.SeatType;

import java.util.UUID;

/** No createdAt/updatedAt — the seats table has neither column, matching {@link Seat} exactly. */
public record SeatResponse(
        UUID id,
        UUID venueId,
        String section,
        String row,
        Integer seatNumber,
        SeatType seatType
) {

    public static SeatResponse from(Seat seat) {
        // .getVenue().getId() only reads the lazy association's id, which a
        // Hibernate proxy always has without needing an open session or
        // triggering a query — same safe pattern used in booking-service's
        // BookingSeatDto.
        return new SeatResponse(
                seat.getId(),
                seat.getVenue().getId(),
                seat.getSection(),
                seat.getRow(),
                seat.getSeatNumber(),
                seat.getSeatType());
    }
}
