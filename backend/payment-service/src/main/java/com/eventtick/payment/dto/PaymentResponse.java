package com.eventtick.payment.dto;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response for payment creation and lookup. */
public record PaymentResponse(
        UUID id,
        UUID bookingId,
        UUID userId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerReference,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
