package com.eventtick.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /api/payments}.
 *
 * <p>{@code amount}/{@code currency} are deliberately not fields here —
 * per the approved Step 1 design (docs/architecture.md §25.1), the charged
 * amount is always computed server-side from the booking's own
 * {@code total_amount}, never accepted from the client.
 *
 * <p>{@code userId} is deliberately also not a field — unlike
 * booking-service's {@code CreateBookingRequest} (which still trusts a
 * client-supplied {@code userId}, a documented pre-existing gap), the
 * caller's identity here is always derived from the validated JWT
 * ({@code Authentication}), never the request body.
 *
 * <p>{@code provider} is deliberately also not a field: exactly one
 * {@link com.eventtick.payment.provider.PaymentProvider} bean is wired for
 * this step ({@code MockPaymentProvider}), selected server-side via
 * {@code payment.default-provider} configuration, not per-request — a
 * client choosing its own provider isn't part of this design.
 */
public record CreatePaymentRequest(
        @NotNull UUID bookingId,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
