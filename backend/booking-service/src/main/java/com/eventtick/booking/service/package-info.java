/**
 * Booking creation, cancellation, and seat-reservation logic:
 * {@link com.eventtick.booking.service.ShowSeatQueryService} (read-only
 * seat map) and {@link com.eventtick.booking.service.BookingService}
 * (hold/release/create/confirm/cancel/history).
 *
 * <p>The transactional concurrency control — row-level locking
 * ({@code SELECT ... FOR UPDATE}) on {@code show_seats} inside
 * {@code @Transactional} methods — that prevents double booking is
 * implemented here; see {@code BookingService}'s class Javadoc for what
 * it does and does not cover (in particular: no hold ownership or expiry
 * without Redis, no authorization without the future auth layer).
 */
package com.eventtick.booking.service;
