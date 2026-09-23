package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request body for creating or updating a {@link com.eventtick.catalog.entity.Seat}.
 * One shape for both — {@code SeatService.create}/{@code update} take
 * identical parameters, including which venue the seat belongs to (a
 * seat can be reassigned to a different venue via update, same as every
 * other field).
 */
public record SeatRequest(
        @NotNull UUID venueId,
        @NotBlank String section,
        @NotBlank String row,
        @NotNull @Positive Integer seatNumber,
        @NotNull SeatType seatType
) {
}
