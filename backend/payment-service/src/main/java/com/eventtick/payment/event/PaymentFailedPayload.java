package com.eventtick.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The {@code PaymentFailed} domain payload (Phase 16 Step 5) — same
 * "IDs-only, minimal fields" rule as {@link PaymentSucceededPayload}
 * (§47.5), plus one addition: {@code failureReason}, since a downstream
 * consumer (or a human reading the audit trail) otherwise has no way to
 * tell a declined charge from any other failure. Sourced from {@link
 * com.eventtick.payment.provider.PaymentProviderResult#failureReason()} —
 * not persisted anywhere on {@link com.eventtick.payment.entity.Payment}
 * itself (that entity has no such column, and adding one purely to feed
 * this event would be schema change this step doesn't need), so it only
 * ever exists here, in the event this payment's one and only failure
 * actually raises.
 *
 * <p>Deliberately excludes the same fields {@code PaymentSucceededPayload}
 * excludes, for the same reasons: {@code idempotencyKey}, {@code status}
 * (the event type already says {@code FAILED} — terminal, one-way),
 * {@code provider}, any JWT/auth data, and denormalized booking data
 * beyond {@code bookingId}. {@code providerReference} IS included, exactly
 * as for {@code PaymentSucceeded} — it may be {@code null} (a failure that
 * never reached the provider, e.g. this project's own
 * {@code MockPaymentProvider}'s forced-failure test path), which is not an
 * error, just the honest reflection of {@code PaymentProviderResult}'s own
 * documented contract.
 */
public record PaymentFailedPayload(
        UUID paymentId,
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String providerReference,
        String failureReason
) {
}
