package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.InvalidBookingStateException;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.outbox.OutboxService;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 15 Step 3: {@link BookingService#confirmBooking}/{@link
 * BookingService#cancelBooking}'s new idempotent-no-op behavior on an
 * already-terminal booking — added so payment-service's reconciliation
 * sweep (docs/architecture.md §25.3) can safely retry a confirm/cancel call
 * whose original response was lost, without risking a double seat-status
 * transition or a spurious error. Plain Mockito unit test, mirroring
 * {@code BookingServiceListAllTest}'s style — proves the service's own
 * internals, not an HTTP contract (the {@code @WebMvcTest}-level
 * {@code BookingControllerRegressionTest} mocks this service entirely and
 * so can't prove anything about it).
 *
 * <p>Every pre-existing behavior (successful confirm/cancel from a valid
 * starting state, rejection from an invalid one) is re-asserted here
 * unchanged — this class does not weaken anything Phase 13 already proved.
 */
class BookingServiceConfirmCancelIdempotencyTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final BookingService bookingService =
            new BookingService(bookingRepository, bookingSeatRepository, showSeatRepository,
                    mock(OutboxService.class), java.time.Duration.ofMinutes(10));

    private static Booking booking(UUID id, BookingStatus status) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUserId(UUID.randomUUID());
        booking.setShowId(UUID.randomUUID());
        booking.setStatus(status);
        booking.setTotalAmount(new BigDecimal("50.00"));
        return booking;
    }

    private static ShowSeat heldSeat(UUID id) {
        ShowSeat seat = mock(ShowSeat.class);
        when(seat.getId()).thenReturn(id);
        when(seat.getStatus()).thenReturn(ShowSeatStatus.HELD);
        return seat;
    }

    // ---- confirmBooking: pre-existing behavior, unchanged ----

    @Test
    void confirmBooking_pending_transitionsToConfirmed_andBooksSeats() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.PENDING);
        UUID seatId = UUID.randomUUID();
        BookingSeat lineItem = mock(BookingSeat.class);
        ShowSeat seat = heldSeat(seatId);
        when(lineItem.getShowSeat()).thenReturn(seat);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(lineItem));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.confirmBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(seat).setStatus(ShowSeatStatus.BOOKED);
    }

    @Test
    void confirmBooking_cancelled_stillThrows_notTreatedAsIdempotent() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(bookingId))
                .isInstanceOf(InvalidBookingStateException.class);
        verify(bookingRepository, never()).save(any());
    }

    // ---- confirmBooking: new idempotent no-op ----

    @Test
    void confirmBooking_alreadyConfirmed_isANoOp_returnsTheBookingWithoutError() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        Booking result = bookingService.confirmBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result).isSameAs(booking);
        // No seat lock, no save — a true no-op, not a re-confirmation that
        // happens to land on the same status.
        verify(bookingSeatRepository, never()).findByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    // ---- cancelBooking: pre-existing behavior, unchanged ----

    @Test
    void cancelBooking_pending_transitionsToCancelled_andReleasesSeats() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.PENDING);
        UUID seatId = UUID.randomUUID();
        BookingSeat lineItem = mock(BookingSeat.class);
        ShowSeat seat = heldSeat(seatId);
        when(lineItem.getShowSeat()).thenReturn(seat);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(lineItem));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.cancelBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(seat).setStatus(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void cancelBooking_failed_stillThrows_notTreatedAsIdempotent() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.FAILED);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    // ---- cancelBooking: new idempotent no-op ----

    @Test
    void cancelBooking_alreadyCancelled_isANoOp_returnsTheBookingWithoutError() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking));

        Booking result = bookingService.cancelBooking(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result).isSameAs(booking);
        verify(bookingSeatRepository, never()).findByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }
}
