package com.eventtick.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a {@link PaymentProvider} needs to attempt a charge. Deliberately
 * carries no payment-instrument data (card number, CVV, bank details,
 * etc.) — see docs/architecture.md §25.1: this project never accepts or
 * stores that data at all, mock or real.
 */
public record PaymentChargeRequest(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
}
