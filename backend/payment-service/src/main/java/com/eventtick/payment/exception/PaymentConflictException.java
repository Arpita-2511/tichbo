package com.eventtick.payment.exception;

import java.util.UUID;

/**
 * A business-rule conflict on payment creation that isn't specifically
 * about idempotency — covers both cases the approved design calls out:
 * (1) the booking already has a live (non-terminal-failure) payment
 * ({@code PaymentStatus#isLive()}), and (2) the booking itself isn't in a
 * payable state (not {@code PENDING} — e.g. already cancelled).
 * Deliberately one exception class for both, rather than two near-identical
 * ones, per "use the minimum set required".
 */
public class PaymentConflictException extends RuntimeException {

    private PaymentConflictException(String message) {
        super(message);
    }

    public static PaymentConflictException duplicateLivePayment(UUID bookingId) {
        return new PaymentConflictException("Booking " + bookingId + " already has a live payment.");
    }

    public static PaymentConflictException bookingNotPayable(UUID bookingId, String bookingStatus) {
        return new PaymentConflictException(
                "Booking " + bookingId + " is in status " + bookingStatus + " and cannot accept a payment.");
    }
}
