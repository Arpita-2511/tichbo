package com.eventtick.payment.entity;

/**
 * Whether the booking-service side effect of a terminal payment (confirm
 * for {@link PaymentStatus#SUCCESS}, release/cancel for {@link
 * PaymentStatus#FAILED}/{@link PaymentStatus#EXPIRED}) has actually been
 * acknowledged by booking-service — the field the Phase 15 Step 3
 * reconciliation sweep uses to find work, see
 * {@code database/migrations/0012_add_booking_sync_status_to_payments.up.sql}
 * and {@code docs/architecture.md} §25.3.
 *
 * <p>Deliberately internal-only: never exposed on {@code PaymentResponse}
 * (see docs/architecture.md §25.3's "observability" note — prefer logs
 * over a new public field without a strong reason). {@link
 * PaymentStatus#CANCELLED} payments never need syncing (Step 3 never gives
 * {@code CREATED -> CANCELLED} a booking-side effect to reconcile), so this
 * field is meaningless — left at its default — for anything other than
 * {@code SUCCESS}/{@code FAILED}/{@code EXPIRED} payments.
 */
public enum BookingSyncStatus {
    /** Booking-service has not (yet, or successfully) been told the outcome. Reconciliation candidate. */
    PENDING,
    /** Booking-service acknowledged the confirm/cancel call. Nothing left to do. */
    DONE
}
