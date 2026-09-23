/**
 * Custom exceptions thrown by the Catalog Service's service layer —
 * {@link com.eventtick.catalog.exception.CatalogEntityNotFoundException}
 * — and {@link com.eventtick.catalog.exception.GlobalExceptionHandler},
 * which maps it (and standard Spring/bean-validation failures) to HTTP
 * responses for {@code com.eventtick.catalog.controller}.
 *
 * <p>Basic input validation (blank title, non-positive numbers, invalid
 * time ranges) throws plain {@code IllegalArgumentException} instead of a
 * dedicated exception type, matching booking-service's convention.
 */
package com.eventtick.catalog.exception;
