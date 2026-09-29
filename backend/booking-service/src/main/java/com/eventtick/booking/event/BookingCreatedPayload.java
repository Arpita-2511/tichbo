package com.eventtick.booking.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The {@code BookingCreated} domain payload (docs/architecture.md §47.2).
 * IDs and the minimal fields a consumer needs to decide whether to act —
 * not the whole {@code Booking}/{@code ShowSeat} objects (the IDs-only
 * payload rule, §47.5) — and deliberately leaner than that design table's
 * own illustrative column list: {@code status} is omitted (always
 * {@code PENDING} for a just-created booking — the event type already says
 * so) and so is a separate {@code createdAt} (the envelope's own
 * {@code occurredAt} already carries that exact timestamp; duplicating it
 * inside the payload too would say nothing new).
 */
public record BookingCreatedPayload(
        UUID bookingId,
        UUID userId,
        UUID showId,
        List<UUID> seatIds,
        BigDecimal totalAmount
) {
}
