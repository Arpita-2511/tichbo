/**
 * REST controllers exposing the Booking Service's public API:
 * {@link com.eventtick.booking.controller.BookingController} — seat map,
 * hold/release, booking creation, confirmation, cancellation, and
 * per-user booking history. Delegates entirely to
 * {@code com.eventtick.booking.service}; exception-to-HTTP-status mapping
 * lives in {@code com.eventtick.booking.exception.GlobalExceptionHandler}.
 */
package com.eventtick.booking.controller;
