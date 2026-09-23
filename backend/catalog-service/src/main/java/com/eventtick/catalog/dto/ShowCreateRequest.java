package com.eventtick.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for creating a {@link com.eventtick.catalog.entity.Show}.
 * No {@code status} field — {@code ShowService.create} always starts a
 * new show as {@code SCHEDULED} internally; that's not a client decision
 * (mirrors {@code CreateBookingRequest} never accepting a booking
 * status in booking-service). Changing status later is
 * {@link ShowUpdateRequest}'s job, not this one's.
 */
public record ShowCreateRequest(
        @NotNull UUID contentId,
        @NotNull UUID venueId,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}
