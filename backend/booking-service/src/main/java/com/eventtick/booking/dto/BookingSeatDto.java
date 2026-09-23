package com.eventtick.booking.dto;

import com.eventtick.booking.entity.BookingSeat;

import java.math.BigDecimal;
import java.util.UUID;

/** One seat line item within a {@link BookingResponse}. */
public record BookingSeatDto(UUID bookingSeatId, UUID showSeatId, BigDecimal priceAtBooking) {

    public static BookingSeatDto from(BookingSeat bookingSeat) {
        // .getShowSeat().getId() only reads the lazy association's id, which
        // a Hibernate proxy always has without needing an open session or
        // triggering a query — safe even though open-in-view is disabled.
        return new BookingSeatDto(
                bookingSeat.getId(),
                bookingSeat.getShowSeat().getId(),
                bookingSeat.getPriceAtBooking());
    }
}
