/**
 * Custom exceptions thrown by {@code BookingService}:
 * {@link com.eventtick.booking.exception.BookingNotFoundException},
 * {@link com.eventtick.booking.exception.InvalidBookingStateException},
 * {@link com.eventtick.booking.exception.InvalidSeatStateException}, and
 * {@link com.eventtick.booking.exception.SeatShowMismatchException}.
 *
 * <p>All are unchecked ({@code RuntimeException}). A global exception
 * handler mapping these to HTTP responses is not implemented yet — that's
 * a controller-layer concern, and no controllers exist yet.
 */
package com.eventtick.booking.exception;
