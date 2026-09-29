package com.eventtick.payment.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The subset of booking-service's own {@code BookingResponse} (see
 * {@code backend/booking-service/.../dto/BookingResponse.java}) that
 * payment-service actually needs: who owns the booking, its current
 * status, and its authoritative charge amount. Deliberately a separate,
 * smaller DTO rather than sharing booking-service's class — the two
 * services share no code, exactly like every other cross-service boundary
 * in this project. {@code status} is a plain {@code String} (not
 * booking-service's {@code BookingStatus} enum), for the same reason
 * {@code Booking.userId} is a plain {@code UUID} rather than a shared
 * entity reference — no dependency on booking-service's code at all.
 */
public record BookingSummary(UUID bookingId, UUID userId, String status, BigDecimal totalAmount) {

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
