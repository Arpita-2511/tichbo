package com.eventtick.booking.dto;

import java.util.List;
import java.util.UUID;

/** Response for {@code GET /api/bookings/shows/{showId}/seats}. */
public record SeatMapResponse(UUID showId, List<SeatMapItemDto> seats) {
}
