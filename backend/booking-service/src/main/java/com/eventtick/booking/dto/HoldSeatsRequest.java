package com.eventtick.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/bookings/shows/{showId}/seats/hold}.
 *
 * <p>{@code userId} is accepted only because
 * {@code BookingService.holdSeats} already accepts it — it is currently
 * unused by that method (not stored, not checked). See
 * {@code BookingService}'s class Javadoc ("Hold ownership"): hold
 * ownership is a gap this DTO does not paper over, it's a future
 * Redis-backed concern.
 */
public record HoldSeatsRequest(
        @NotNull UUID userId,
        @NotEmpty List<UUID> showSeatIds
) {
}
