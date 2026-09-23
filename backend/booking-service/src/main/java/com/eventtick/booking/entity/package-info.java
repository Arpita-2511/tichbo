/**
 * JPA entities mapping to the {@code bookings}, {@code booking_seats},
 * and {@code show_seats} tables: {@link com.eventtick.booking.entity.Booking},
 * {@link com.eventtick.booking.entity.BookingSeat}, and
 * {@link com.eventtick.booking.entity.ShowSeat}.
 *
 * <p>These mirror the hand-authored schema in {@code database/migrations/}
 * exactly — {@code ddl-auto} is set to {@code none} (see
 * {@code application.yml}), so Hibernate never generates or alters schema;
 * the SQL migrations remain the single source of truth. Columns owned by
 * other services (user-service's {@code users}, catalog-service's
 * {@code shows}/{@code seats}) are mapped as plain UUID reference fields,
 * not JPA associations — see {@code ShowSeat}'s class Javadoc for why.
 */
package com.eventtick.booking.entity;
