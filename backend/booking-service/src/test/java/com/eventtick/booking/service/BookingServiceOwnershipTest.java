package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.BookingAccessDeniedException;
import com.eventtick.booking.exception.BookingNotFoundException;
import com.eventtick.booking.exception.InvalidBookingStateException;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.outbox.OutboxService;
import com.eventtick.booking.repository.ShowSeatRepository;
import com.eventtick.booking.security.CallerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-07 booking ownership, at the service layer where the rule lives: who may
 * read, list and cancel which booking, given the caller's identity (taken
 * from the validated JWT by the controller). Plain Mockito, same style as
 * {@code BookingServiceConfirmCancelIdempotencyTest}.
 */
class BookingServiceOwnershipTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final BookingService bookingService =
            new BookingService(bookingRepository, bookingSeatRepository, showSeatRepository,
                    mock(OutboxService.class), java.time.Duration.ofMinutes(10));

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private static CallerIdentity customer(UUID id) {
        return new CallerIdentity(id, false);
    }

    private static CallerIdentity admin() {
        return new CallerIdentity(ADMIN, true);
    }

    private static Booking booking(UUID id, UUID userId, BookingStatus status) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUserId(userId);
        booking.setShowId(UUID.randomUUID());
        booking.setStatus(status);
        booking.setTotalAmount(new BigDecimal("50.00"));
        return booking;
    }

    // ---- GET one booking ----

    @Test
    void getBookingForCaller_owner_isAllowed() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.PENDING);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThat(bookingService.getBookingForCaller(id, customer(OWNER))).isSameAs(booking);
    }

    @Test
    void getBookingForCaller_differentCustomer_isDenied() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking(id, OWNER, BookingStatus.PENDING)));

        assertThatThrownBy(() -> bookingService.getBookingForCaller(id, customer(OTHER)))
                .isInstanceOf(BookingAccessDeniedException.class);
    }

    @Test
    void getBookingForCaller_admin_isAllowedForAnyBooking() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThat(bookingService.getBookingForCaller(id, admin())).isSameAs(booking);
    }

    @Test
    void getBookingForCaller_nonexistent_isNotFound_notForbidden_evenForACustomer() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingForCaller(id, customer(OTHER)))
                .isInstanceOf(BookingNotFoundException.class);
    }

    // ---- list ----

    @Test
    void getBookingsForCaller_customer_getsOnlyTheirOwn() {
        Booking mine = booking(UUID.randomUUID(), OWNER, BookingStatus.PENDING);
        when(bookingRepository.findByUserId(OWNER)).thenReturn(List.of(mine));

        assertThat(bookingService.getBookingsForCaller(customer(OWNER), null)).containsExactly(mine);
        verify(bookingRepository, never()).findAll();
        verify(bookingRepository, never()).findByUserId(OTHER);
    }

    @Test
    void getBookingsForCaller_customer_namingThemselves_isFine() {
        Booking mine = booking(UUID.randomUUID(), OWNER, BookingStatus.PENDING);
        when(bookingRepository.findByUserId(OWNER)).thenReturn(List.of(mine));

        assertThat(bookingService.getBookingsForCaller(customer(OWNER), OWNER)).containsExactly(mine);
    }

    @Test
    void getBookingsForCaller_customer_namingSomeoneElse_isDenied_andNothingIsQueried() {
        assertThatThrownBy(() -> bookingService.getBookingsForCaller(customer(OTHER), OWNER))
                .isInstanceOf(BookingAccessDeniedException.class);

        verify(bookingRepository, never()).findByUserId(any());
        verify(bookingRepository, never()).findAll();
    }

    @Test
    void getBookingsForCaller_admin_withNoUserId_getsEveryBooking() {
        Booking a = booking(UUID.randomUUID(), OWNER, BookingStatus.PENDING);
        Booking b = booking(UUID.randomUUID(), OTHER, BookingStatus.CONFIRMED);
        when(bookingRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(bookingService.getBookingsForCaller(admin(), null)).containsExactly(a, b);
    }

    @Test
    void getBookingsForCaller_admin_namingAUser_getsThatUsersBookings() {
        Booking a = booking(UUID.randomUUID(), OWNER, BookingStatus.PENDING);
        when(bookingRepository.findByUserId(OWNER)).thenReturn(List.of(a));

        assertThat(bookingService.getBookingsForCaller(admin(), OWNER)).containsExactly(a);
        verify(bookingRepository, never()).findAll();
    }

    // ---- cancel ----

    private ShowSeat stubSeatsForCancel(UUID bookingId) {
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = mock(ShowSeat.class);
        when(seat.getId()).thenReturn(seatId);
        when(seat.getStatus()).thenReturn(ShowSeatStatus.HELD);
        BookingSeat lineItem = mock(BookingSeat.class);
        when(lineItem.getShowSeat()).thenReturn(seat);
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(lineItem));
        when(showSeatRepository.lockAllByIdIn(List.of(seatId))).thenReturn(List.of(seat));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return seat;
    }

    @Test
    void cancelBookingForCaller_owner_cancelsAndReleasesSeats() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.PENDING);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        ShowSeat seat = stubSeatsForCancel(id);

        Booking result = bookingService.cancelBookingForCaller(id, customer(OWNER));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(seat).setStatus(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void cancelBookingForCaller_differentCustomer_isDenied_andNothingChanges() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.PENDING);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBookingForCaller(id, customer(OTHER)))
                .isInstanceOf(BookingAccessDeniedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookingSeatRepository, never()).findByBookingId(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingForCaller_admin_isNotExempt_becauseFr39AdminBookingManagementIsReadOnly() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.CONFIRMED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBookingForCaller(id, admin()))
                .isInstanceOf(BookingAccessDeniedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBookingForCaller_nonexistent_isNotFound_notForbidden() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBookingForCaller(id, customer(OTHER)))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancelBookingForCaller_owner_stillSubjectToTheBookingStateMachine() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking(id, OWNER, BookingStatus.FAILED)));

        assertThatThrownBy(() -> bookingService.cancelBookingForCaller(id, customer(OWNER)))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void cancelBookingForCaller_owner_alreadyCancelled_isTheExistingIdempotentNoOp() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.CANCELLED);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

        assertThat(bookingService.cancelBookingForCaller(id, customer(OWNER))).isSameAs(booking);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void systemCancel_hasNoOwnershipCheck_forPaymentServiceReleases() {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, OWNER, BookingStatus.PENDING);
        when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));
        stubSeatsForCancel(id);

        assertThat(bookingService.cancelBooking(id).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
