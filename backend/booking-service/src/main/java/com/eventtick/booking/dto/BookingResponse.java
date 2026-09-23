package com.eventtick.booking.dto;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for booking creation/confirmation/cancellation and the
 * per-user booking list.
 *
 * <p>{@code createdAt}/{@code updatedAt} may be {@code null} in the
 * response to the very call that created the booking — those columns are
 * database-generated ({@code insertable/updatable = false} on the
 * entity), so a freshly built {@code Booking} object doesn't have them
 * populated until the row is re-read. A subsequent {@code GET} would show
 * real values.
 */
public record BookingResponse(
        UUID bookingId,
        UUID userId,
        UUID showId,
        BookingStatus status,
        BigDecimal totalAmount,
        List<BookingSeatDto> seats,
        Instant createdAt,
        Instant updatedAt
) {

    public static BookingResponse from(Booking booking, List<BookingSeat> bookingSeats) {
        List<BookingSeatDto> seatDtos = bookingSeats.stream().map(BookingSeatDto::from).toList();
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getShowId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                seatDtos,
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
