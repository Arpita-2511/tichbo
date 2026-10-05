package com.eventtick.booking.dto;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One seat's state within a show's seat map. */
public record SeatMapItemDto(UUID showSeatId, UUID seatId, ShowSeatStatus status, BigDecimal price) {

    /**
     * Maps a {@link ShowSeat} entity to its DTO representation. Applies
     * lazy expiration: a seat whose status is HELD but whose hold has
     * expired is reported as AVAILABLE so the seat map reflects reality
     * even between scheduled cleanup runs.
     */
    public static SeatMapItemDto from(ShowSeat showSeat) {
        ShowSeatStatus effectiveStatus = showSeat.getStatus();
        if (effectiveStatus == ShowSeatStatus.HELD
                && showSeat.getHoldExpiresAt() != null
                && showSeat.getHoldExpiresAt().isBefore(Instant.now())) {
            effectiveStatus = ShowSeatStatus.AVAILABLE;
        }
        return new SeatMapItemDto(showSeat.getId(), showSeat.getSeatId(), effectiveStatus, showSeat.getPrice());
    }
}
