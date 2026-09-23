/**
 * Spring Data JPA repositories for the {@code show_seats}, {@code bookings},
 * and {@code booking_seats} tables (see {@code database/migrations/0008}–
 * {@code 0010}), which this service exclusively owns:
 * {@link com.eventtick.booking.repository.ShowSeatRepository},
 * {@link com.eventtick.booking.repository.BookingRepository}, and
 * {@link com.eventtick.booking.repository.BookingSeatRepository}.
 *
 * <p>Each currently provides only standard {@code JpaRepository} CRUD,
 * paging, and sorting — no custom finder or locking queries yet. The
 * seat-locking queries (e.g. {@code SELECT ... FOR UPDATE} on
 * {@code show_seats}) the booking-concurrency logic will need are not
 * implemented yet.
 */
package com.eventtick.booking.repository;
