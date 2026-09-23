/**
 * REST controllers exposing the Catalog Service's public API under
 * {@code /api/catalog/**}:
 * {@link com.eventtick.catalog.controller.ContentController},
 * {@link com.eventtick.catalog.controller.VenueController},
 * {@link com.eventtick.catalog.controller.SeatController}, and
 * {@link com.eventtick.catalog.controller.ShowController}. Each is a thin
 * CRUD delegator to its matching service in
 * {@code com.eventtick.catalog.service}; exception-to-HTTP-status mapping
 * lives in {@code com.eventtick.catalog.exception.GlobalExceptionHandler}.
 *
 * <p>Show-specific seat-availability (seat map) endpoints are not here —
 * {@code show_seats} is owned by booking-service, not this service.
 */
package com.eventtick.catalog.controller;
