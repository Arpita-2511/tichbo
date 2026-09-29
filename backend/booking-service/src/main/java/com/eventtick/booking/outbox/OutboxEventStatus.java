package com.eventtick.booking.outbox;

/**
 * {@code chk_booking_outbox_events_status}. {@code FAILED} is reserved for
 * a future max-retry/DLQ policy (docs/architecture.md §48) — no code path
 * sets it yet, the same "defined but not yet reachable" precedent as
 * {@code BookingStatus.FAILED}. Today a publish failure simply leaves a row
 * {@code PENDING} with {@code attempts} incremented, for the next sweep.
 */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
