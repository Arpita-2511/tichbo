/**
 * Persistence and basic validation for the Catalog domain:
 * {@link com.eventtick.catalog.service.ContentService},
 * {@link com.eventtick.catalog.service.VenueService},
 * {@link com.eventtick.catalog.service.SeatService}, and
 * {@link com.eventtick.catalog.service.ShowService}. Each provides
 * create/get/list/update/delete for its entity, using constructor-injected
 * repositories.
 *
 * <p>Show-specific seat availability ({@code show_seats}) is not handled
 * here — that table, and any service logic over it, belongs to
 * booking-service (see {@code docs/architecture.md} §17). Search/filtering
 * beyond the basic operations above is not implemented yet either.
 */
package com.eventtick.catalog.service;
