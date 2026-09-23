/**
 * Request/response DTOs for the Booking Service's public API, kept
 * separate from JPA entities so the API contract can evolve independently
 * of the persistence model. Implements the API contract approved for the
 * seven endpoints in {@code com.eventtick.booking.controller.BookingController}.
 *
 * <p>{@link com.eventtick.booking.dto.ErrorResponse} is the uniform error
 * body produced by {@code com.eventtick.booking.exception.GlobalExceptionHandler}.
 */
package com.eventtick.booking.dto;
