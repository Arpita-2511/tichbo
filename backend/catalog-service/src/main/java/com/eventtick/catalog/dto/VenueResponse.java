package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.Venue;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String address,
        String city,
        Instant createdAt,
        Instant updatedAt
) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getCity(),
                venue.getCreatedAt(),
                venue.getUpdatedAt());
    }
}
