package com.eventtick.booking.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Request body for {@code POST /api/bookings/shows/{showId}/seats/release}. */
public record ReleaseSeatsRequest(@NotEmpty List<UUID> showSeatIds) {
}
