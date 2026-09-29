package com.eventtick.payment.exception;

import java.util.UUID;

/**
 * Thrown on {@code POST /api/payments} when the authenticated caller does
 * not own the referenced booking — ownership is always derived from the
 * validated JWT's {@code sub} claim compared against the booking's own
 * {@code userId} (from booking-service), never a client-supplied value.
 * See docs/architecture.md §25.1's security model.
 */
public class PaymentOwnershipException extends RuntimeException {

    public PaymentOwnershipException(UUID bookingId) {
        super("You do not own booking " + bookingId + ".");
    }
}
