package com.eventtick.catalog.entity;

/**
 * Mirrors the {@code chk_content_type} CHECK constraint on the
 * {@code content} table (see
 * {@code database/migrations/0004_create_content_table.up.sql}).
 */
public enum ContentType {
    MOVIE,
    SPORTS_MATCH,
    CONCERT,
    THEATRE,
    EVENT
}
