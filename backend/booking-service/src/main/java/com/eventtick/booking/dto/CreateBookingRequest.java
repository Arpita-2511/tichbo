package com.eventtick.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/bookings}.
 *
 * <p>{@code userId} is caller-supplied — there is no authentication layer
 * yet to derive it from a request principal.
 */
public record CreateBookingRequest(
        @NotNull UUID userId,
        @NotNull UUID showId,
        @NotEmpty List<UUID> showSeatIds
) {
}
