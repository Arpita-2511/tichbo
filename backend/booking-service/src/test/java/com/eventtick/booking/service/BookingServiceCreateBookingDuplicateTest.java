package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.InvalidSeatStateException;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.outbox.OutboxService;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceCreateBookingDuplicateTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final BookingService bookingService =
            new BookingService(bookingRepository, bookingSeatRepository, showSeatRepository,
                    mock(OutboxService.class), Duration.ofMinutes(10));

    private static final UUID USER = UUID.randomUUID();

    private static ShowSeat heldSeat(UUID id, UUID showId) {
        ShowSeat seat = mock(ShowSeat.class);
        when(seat.getId()).thenReturn(id);
        when(seat.getShowId()).thenReturn(showId);
        when(seat.getStatus()).thenReturn(ShowSeatStatus.HELD);
        when(seat.getPrice()).thenReturn(new BigDecimal("500.00"));
        when(seat.getHolderUserId()).thenReturn(USER);
        when(seat.getHoldExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
        return seat;
    }

    @Test
    void createBooking_seatAlreadyInAnActiveBooking_isRejected_andNothingIsSaved() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeat(seatId, showId);
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingSeatRepository.findShowSeatIdsWithBookingInStatus(anyCollection(), anyCollection()))
                .thenReturn(List.of(seatId));

        assertThatThrownBy(() -> bookingService.createBooking(USER, showId, List.of(seatId), "corr-test"))
                .isInstanceOf(InvalidSeatStateException.class)
                .hasMessageContaining(seatId.toString())
                .hasMessageContaining("active booking");

        verify(bookingRepository, never()).save(any());
        verify(bookingSeatRepository, never()).saveAll(any());
    }

    @Test
    void createBooking_onlyPendingAndConfirmedCountAsActive() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeat(seatId, showId);
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingSeatRepository.findShowSeatIdsWithBookingInStatus(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.createBooking(USER, showId, List.of(seatId), "corr-test");

        verify(bookingSeatRepository).findShowSeatIdsWithBookingInStatus(
                eq(List.of(seatId)), eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)));
    }

    @Test
    void createBooking_heldSeatWithNoActiveBooking_stillSucceeds() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeat(seatId, showId);
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingSeatRepository.findShowSeatIdsWithBookingInStatus(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking created = bookingService.createBooking(USER, showId, List.of(seatId), "corr-test");

        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("500.00");
    }
}
