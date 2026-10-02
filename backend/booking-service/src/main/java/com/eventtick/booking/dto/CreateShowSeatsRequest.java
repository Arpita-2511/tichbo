package com.eventtick.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Request body for {@code POST /api/admin/shows/{showId}/seats}. */
public record CreateShowSeatsRequest(@NotEmpty @Valid List<ShowSeatDefinition> seats) {
}
