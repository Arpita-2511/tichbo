package com.eventtick.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The {@code PaymentSucceeded} domain payload (Phase 16 Step 4,
 * docs/architecture.md §50). IDs and the minimal fields a downstream
 * consumer needs — the same "IDs-only payload rule" (§47.5) already
 * applied to {@code BookingCreatedPayload} — not the whole {@code Payment}
 * entity.
 *
 * <p>Deliberately excludes: {@code idempotencyKey} (an internal
 * de-duplication detail of payment-service's own API, meaningless to a
 * downstream consumer, and unnecessary to expose), {@code status} (the
 * event type already says {@code SUCCESS} — a terminal, one-way state per
 * {@link com.eventtick.payment.entity.PaymentStatus}, so nothing else is
 * possible), {@code provider} (the mock/real payment provider's own name —
 * an implementation detail, not something a consumer needs to decide
 * anything), any JWT/authentication data, and any denormalized booking
 * data beyond the bare {@code bookingId} reference (a consumer that needs
 * more about the booking can consume {@code BookingCreated} itself, or a
 * future {@code BookingConfirmed}, rather than this event carrying it
 * secondhand).
 *
 * <p>{@code providerReference} IS included — it is the one piece of
 * externally-meaningful evidence that a real charge happened, and is
 * exactly what this step's own live-verification requirement (§50.8) checks
 * against the authoritative {@code payments.provider_reference} row.
 */
public record PaymentSucceededPayload(
        UUID paymentId,
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String providerReference
) {
}
