package com.eventtick.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The {@code PaymentExpired} domain payload (Phase 16 Step 5) — same
 * "IDs-only, minimal fields" rule as {@link PaymentSucceededPayload}
 * (§47.5). No {@code providerReference}: a payment that expires was never
 * resolved by the provider at all (the whole point of {@code EXPIRED} is
 * that nothing — success or failure — ever came back in time), so the
 * field would always be {@code null} and carries no information; omitted
 * rather than included-but-always-empty. No failure-reason-style field
 * either — unlike {@code PaymentFailed}, there is nothing to distinguish:
 * every expiry has exactly one cause (the provider never responded inside
 * {@code payment.expiration-minutes}), which the event type itself already
 * says.
 */
public record PaymentExpiredPayload(
        UUID paymentId,
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency
) {
}
