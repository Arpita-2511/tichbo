package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.ShowStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for updating a {@link com.eventtick.catalog.entity.Show}.
 * Unlike {@link ShowCreateRequest}, includes {@code status} — a client
 * updating a show (e.g. cancelling or marking it completed) is exactly
 * how {@code ShowService.update} exposes that state change, since no
 * separate status-transition method exists.
 */
public record ShowUpdateRequest(
        @NotNull UUID contentId,
        @NotNull UUID venueId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull ShowStatus status
) {
}
