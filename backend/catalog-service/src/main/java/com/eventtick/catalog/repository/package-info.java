/**
 * Spring Data JPA repositories for the {@code content}, {@code venues},
 * {@code seats}, and {@code shows} tables (see
 * {@code database/migrations/0004}–{@code 0007}), which this service
 * exclusively owns: {@link com.eventtick.catalog.repository.ContentRepository},
 * {@link com.eventtick.catalog.repository.VenueRepository},
 * {@link com.eventtick.catalog.repository.SeatRepository}, and
 * {@link com.eventtick.catalog.repository.ShowRepository}.
 * {@code show_seats} is owned by booking-service, not this service — see
 * {@code docs/architecture.md} §17; no repository for it exists here.
 *
 * <p>Each currently provides only standard {@code JpaRepository} CRUD,
 * paging, and sorting, plus the handful of finder methods the entity
 * layer's relationship design requires (see each repository's Javadoc).
 */
package com.eventtick.catalog.repository;
