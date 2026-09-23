package com.eventtick.catalog.entity;

/**
 * Mirrors the {@code chk_seats_seat_type} CHECK constraint on the
 * {@code seats} table (see
 * {@code database/migrations/0006_create_seats_table.up.sql}).
 *
 * <p>This is the physical seat's category (e.g. a stadium's VIP box) —
 * unrelated to {@code plans.name} (the user's subscription tier), which
 * also happens to use {@code VIP}. Do not conflate the two.
 */
public enum SeatType {
    STANDARD,
    PREMIUM,
    VIP
}
