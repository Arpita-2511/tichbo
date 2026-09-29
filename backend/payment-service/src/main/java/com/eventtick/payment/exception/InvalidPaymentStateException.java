package com.eventtick.payment.exception;

import com.eventtick.payment.entity.PaymentStatus;

import java.util.UUID;

/**
 * Thrown when an operation attempts an illegal {@link PaymentStatus}
 * transition — mirrors booking-service's own
 * {@code InvalidBookingStateException} in shape. Internal only in Step 3
 * (no public endpoint triggers a transition directly; only
 * {@code PaymentService}'s own lifecycle methods and the scheduled
 * expiration/reconciliation sweeps do), but centralizing the check here
 * (via {@link PaymentStatus#canTransitionTo}) means any future
 * public-facing transition automatically inherits the same guard.
 */
public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(UUID paymentId, PaymentStatus currentStatus, PaymentStatus attemptedStatus) {
        super("Payment " + paymentId + " is in status " + currentStatus
                + " and cannot transition to " + attemptedStatus + ".");
    }
}
