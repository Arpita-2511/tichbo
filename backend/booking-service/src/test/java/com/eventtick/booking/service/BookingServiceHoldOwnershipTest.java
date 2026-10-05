package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.InvalidSeatStateException;
import com.eventtick.booking.outbox.OutboxService;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.repository.ShowSeatRepository;
import com.eventtick.booking.security.CallerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 22: Hold ownership and expiration tests at the service layer.
 * Plain Mockito — same style as the existing service tests.
 */
class BookingServiceHoldOwnershipTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final Duration holdDuration = Duration.ofMinutes(10);
    private final BookingService bookingService =
            new BookingService(bookingRepository, bookingSeatRepository, showSeatRepository,
                    mock(OutboxService.class), holdDuration);

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    private ShowSeat availableSeat(UUID id, UUID showId) {
        ShowSeat seat = new ShowSeat();
        ReflectionTestUtils.setField(seat, "id", id);
        seat.setShowId(showId);
        seat.setSeatId(UUID.randomUUID());
        seat.setStatus(ShowSeatStatus.AVAILABLE);
        seat.setPrice(new BigDecimal("500.00"));
        return seat;
    }

    private ShowSeat heldSeatBy(UUID id, UUID showId, UUID holder, Instant expiresAt) {
        ShowSeat seat = new ShowSeat();
        ReflectionTestUtils.setField(seat, "id", id);
        seat.setShowId(showId);
        seat.setSeatId(UUID.randomUUID());
        seat.setStatus(ShowSeatStatus.HELD);
        seat.setPrice(new BigDecimal("500.00"));
        seat.setHolderUserId(holder);
        seat.setHeldAt(Instant.now().minusSeconds(60));
        seat.setHoldExpiresAt(expiresAt);
        return seat;
    }

    // ---- holdSeats ownership ----

    @Test
    void holdSeats_setsOwnershipFields() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = availableSeat(seatId, showId);
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        List<ShowSeat> result = bookingService.holdSeats(showId, List.of(seatId), USER_A);

        assertThat(result).hasSize(1);
        ShowSeat held = result.get(0);
        assertThat(held.getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(held.getHolderUserId()).isEqualTo(USER_A);
        assertThat(held.getHeldAt()).isNotNull();
        assertThat(held.getHoldExpiresAt()).isNotNull();
        assertThat(held.getHoldExpiresAt()).isAfter(held.getHeldAt());
    }

    @Test
    void holdSeats_holdExpiresAt_isNowPlusConfiguredDuration() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = availableSeat(seatId, showId);
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        Instant before = Instant.now();
        bookingService.holdSeats(showId, List.of(seatId), USER_A);
        Instant after = Instant.now();

        Instant expiresAt = seat.getHoldExpiresAt();
        assertThat(expiresAt).isBetween(before.plus(holdDuration), after.plus(holdDuration));
    }

    // ---- lazy expiration on holdSeats ----

    @Test
    void holdSeats_expiredHold_isLazyExpiredAndSeatBecomesAvailableForNewHold() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, showId, USER_B, Instant.now().minusSeconds(1));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        List<ShowSeat> result = bookingService.holdSeats(showId, List.of(seatId), USER_A);

        assertThat(result.get(0).getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(result.get(0).getHolderUserId()).isEqualTo(USER_A);
    }

    @Test
    void holdSeats_nonExpiredHoldByAnotherUser_isRejected() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, showId, USER_B, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> bookingService.holdSeats(showId, List.of(seatId), USER_A))
                .isInstanceOf(InvalidSeatStateException.class);
    }

    // ---- createBooking ownership ----

    @Test
    void createBooking_seatHeldByRequestingUser_succeeds() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, showId, USER_A, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingSeatRepository.findShowSeatIdsWithBookingInStatus(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking booking = bookingService.createBooking(USER_A, showId, List.of(seatId), "corr");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_seatHeldByDifferentUser_isRejected() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, showId, USER_B, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> bookingService.createBooking(USER_A, showId, List.of(seatId), "corr"))
                .isInstanceOf(InvalidSeatStateException.class)
                .hasMessageContaining("held by another user");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_expiredHold_isRejected() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, showId, USER_A, Instant.now().minusSeconds(1));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> bookingService.createBooking(USER_A, showId, List.of(seatId), "corr"))
                .isInstanceOf(InvalidSeatStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ---- releaseHoldForCaller ----

    @Test
    void releaseHoldForCaller_releasesOwnHold() {
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, UUID.randomUUID(), USER_A, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        bookingService.releaseHoldForCaller(List.of(seatId), new CallerIdentity(USER_A, false));

        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(seat.getHolderUserId()).isNull();
        assertThat(seat.getHoldExpiresAt()).isNull();
    }

    @Test
    void releaseHoldForCaller_doesNotReleaseAnotherUsersHold() {
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, UUID.randomUUID(), USER_B, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        bookingService.releaseHoldForCaller(List.of(seatId), new CallerIdentity(USER_A, false));

        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(seat.getHolderUserId()).isEqualTo(USER_B);
    }

    // ---- releaseHold (system) clears hold fields ----

    @Test
    void releaseHold_clearsHoldFields() {
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeatBy(seatId, UUID.randomUUID(), USER_A, Instant.now().plusSeconds(600));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));

        bookingService.releaseHold(List.of(seatId));

        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(seat.getHolderUserId()).isNull();
        assertThat(seat.getHeldAt()).isNull();
        assertThat(seat.getHoldExpiresAt()).isNull();
    }

    // ---- getHoldDuration ----

    @Test
    void getHoldDuration_returnsConfiguredDuration() {
        assertThat(bookingService.getHoldDuration()).isEqualTo(holdDuration);
    }
}
