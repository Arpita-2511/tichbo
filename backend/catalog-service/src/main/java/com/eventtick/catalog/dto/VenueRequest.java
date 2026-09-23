package com.eventtick.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating or updating a {@link com.eventtick.catalog.entity.Venue}.
 * One shape for both — {@code VenueService.create}/{@code update} take
 * identical parameters.
 */
public record VenueRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String city
) {
}
