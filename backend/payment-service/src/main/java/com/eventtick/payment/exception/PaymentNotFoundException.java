package com.eventtick.payment.exception;

import java.util.UUID;

/** Thrown when a payment cannot be found — either by its own id, or by booking id (no payment exists for that booking). */
public class PaymentNotFoundException extends RuntimeException {

    private PaymentNotFoundException(String message) {
        super(message);
    }

    public static PaymentNotFoundException byId(UUID paymentId) {
        return new PaymentNotFoundException("Payment not found: " + paymentId);
    }

    public static PaymentNotFoundException forBooking(UUID bookingId) {
        return new PaymentNotFoundException("No payment found for booking: " + bookingId);
    }
}
