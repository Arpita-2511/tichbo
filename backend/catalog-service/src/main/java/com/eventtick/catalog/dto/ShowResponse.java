package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;

import java.time.Instant;
import java.util.UUID;

public record ShowResponse(
        UUID id,
        UUID contentId,
        UUID venueId,
        Instant startTime,
        Instant endTime,
        ShowStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ShowResponse from(Show show) {
        // .getContent().getId() / .getVenue().getId() only read each lazy
        // association's id — safe without an open session, same reasoning
        // as SeatResponse.from().
        return new ShowResponse(
                show.getId(),
                show.getContent().getId(),
                show.getVenue().getId(),
                show.getStartTime(),
                show.getEndTime(),
                show.getStatus(),
                show.getCreatedAt(),
                show.getUpdatedAt());
    }
}
