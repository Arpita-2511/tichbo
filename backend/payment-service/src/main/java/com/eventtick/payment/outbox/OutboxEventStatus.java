package com.eventtick.payment.outbox;

/**
 * {@code chk_payment_outbox_events_status}. {@code FAILED} is reserved for
 * a future max-retry/DLQ policy — no code path sets it yet, the same
 * "defined but not yet reachable" precedent as booking-service's own
 * {@code OutboxEventStatus}. Today a publish failure simply leaves a row
 * {@code PENDING} with {@code attempts} incremented, for the next sweep.
 */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
