package com.eventtick.payment.security;

/**
 * Mirrors user-service's {@code UserRole} — a separate copy, not a shared
 * class, since payment-service has no dependency on user-service's code
 * (same reasoning {@code Booking.userId} already uses for not sharing a
 * {@code User} entity across services). Only the two values the {@code
 * role} JWT claim can actually carry.
 */
public enum PaymentRole {
    CUSTOMER,
    ADMIN
}
