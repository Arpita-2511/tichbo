package com.eventtick.catalog.exception;

import java.util.UUID;

/**
 * Thrown when a Catalog Service entity (Content, Venue, Seat, or Show) is
 * requested by id and doesn't exist.
 *
 * <p>One shared exception type covers all four, rather than four
 * near-identical classes ({@code ContentNotFoundException},
 * {@code VenueNotFoundException}, ...) — unlike booking-service, where
 * {@code BookingNotFoundException} was distinct from other failure modes
 * (state conflicts, mismatches), here all four lookups are the same
 * "primary entity not found by id" condition for their respective
 * service, just for different entity types. {@code entityName} keeps the
 * message specific without needing a class per entity.
 */
public class CatalogEntityNotFoundException extends RuntimeException {

    public CatalogEntityNotFoundException(String entityName, UUID id) {
        super(entityName + " not found: " + id);
    }
}
