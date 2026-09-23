package com.eventtick.booking.entity;

/**
 * Mirrors the {@code chk_bookings_status} CHECK constraint on the
 * {@code bookings} table (see
 * {@code database/migrations/0009_create_bookings_table.up.sql}).
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
