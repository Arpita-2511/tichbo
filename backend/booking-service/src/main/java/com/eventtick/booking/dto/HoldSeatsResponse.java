package com.eventtick.booking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response for {@code POST /api/bookings/shows/{showId}/seats/hold}. */
public record HoldSeatsResponse(UUID showId, List<SeatMapItemDto> heldSeats, Instant holdExpiresAt) {
}
