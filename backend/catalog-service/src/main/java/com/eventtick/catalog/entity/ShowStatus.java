package com.eventtick.catalog.entity;

/**
 * Mirrors the {@code chk_shows_status} CHECK constraint on the
 * {@code shows} table (see
 * {@code database/migrations/0007_create_shows_table.up.sql}).
 */
public enum ShowStatus {
    SCHEDULED,
    CANCELLED,
    COMPLETED
}
