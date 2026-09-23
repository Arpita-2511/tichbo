package com.eventtick.booking.dto;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** One seat's state within a show's seat map. */
public record SeatMapItemDto(UUID showSeatId, UUID seatId, ShowSeatStatus status, BigDecimal price) {

    public static SeatMapItemDto from(ShowSeat showSeat) {
        return new SeatMapItemDto(showSeat.getId(), showSeat.getSeatId(), showSeat.getStatus(), showSeat.getPrice());
    }
}
