/**
 * JPA entities mapping to the {@code content}, {@code venues},
 * {@code seats}, and {@code shows} tables:
 * {@link com.eventtick.catalog.entity.Content},
 * {@link com.eventtick.catalog.entity.Venue},
 * {@link com.eventtick.catalog.entity.Seat}, and
 * {@link com.eventtick.catalog.entity.Show}. {@code show_seats} is owned
 * by booking-service, not this service — see
 * {@code docs/architecture.md} §17.
 *
 * <p>These mirror the hand-authored schema in {@code database/migrations/}
 * exactly — {@code ddl-auto} is set to {@code none} (see
 * {@code application.yml}), so Hibernate never generates or alters
 * schema; the SQL migrations remain the single source of truth.
 */
package com.eventtick.catalog.entity;
