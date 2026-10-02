package com.eventtick.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One seat to add to a show's inventory: a catalog-service seat id and its
 * price for this specific show. {@code status} is deliberately not a
 * field here — a newly created show_seats row always starts
 * {@code AVAILABLE} ({@link com.eventtick.booking.service.ShowSeatInventoryService#createSeats}),
 * not a caller-supplied value, the same "no real decision being made"
 * reasoning catalog-service's {@code ShowService.create} already applies
 * to a new Show's status.
 */
public record ShowSeatDefinition(
        @NotNull UUID seatId,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price
) {
}
