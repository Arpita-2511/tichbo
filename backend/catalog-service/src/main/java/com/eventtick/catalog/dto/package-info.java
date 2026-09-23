/**
 * Request/response DTOs for the Catalog Service's public API, kept
 * separate from JPA entities so the API contract can evolve independently
 * of the persistence model: {@code ContentRequest}/{@code ContentResponse},
 * {@code VenueRequest}/{@code VenueResponse}, {@code SeatRequest}/
 * {@code SeatResponse}, and {@code ShowCreateRequest}/
 * {@code ShowUpdateRequest}/{@code ShowResponse}.
 *
 * <p>Response DTOs carry a {@code static from(entity)} factory method,
 * matching the convention already used in booking-service's DTOs. No
 * controllers exist yet to consume these.
 */
package com.eventtick.catalog.dto;
