package com.eventtick.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/bookings/{bookingId}/cancel}.
 *
 * <p>{@code requestingUserId} is accepted but is <b>not</b> checked
 * against the booking's owner — there is no authentication/authorization
 * layer yet to trust it. This field exists so that check has somewhere to
 * go later; it is not enforced today.
 */
public record CancelBookingRequest(@NotNull UUID requestingUserId) {
}
