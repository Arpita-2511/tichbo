package com.eventtick.booking.entity;

/**
 * Mirrors the {@code chk_show_seats_status} CHECK constraint on the
 * {@code show_seats} table (see
 * {@code database/migrations/0008_create_show_seats_table.up.sql}).
 */
public enum ShowSeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}
