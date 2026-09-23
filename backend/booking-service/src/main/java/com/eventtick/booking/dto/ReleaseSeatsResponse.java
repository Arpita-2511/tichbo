package com.eventtick.booking.dto;

import java.util.List;
import java.util.UUID;

/** Response for {@code POST /api/bookings/shows/{showId}/seats/release}. */
public record ReleaseSeatsResponse(UUID showId, List<SeatMapItemDto> releasedSeats) {
}
