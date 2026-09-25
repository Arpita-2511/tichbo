package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 13.7.1: {@link BookingService#listAll(Pageable)}, for
 * {@code GET /api/admin/bookings}. A plain Mockito unit test — mirrors
 * catalog-service's {@code ShowServiceCancelTest} (Phase 13.6.2) — because
 * this proves the service's own delegation, not an HTTP contract (that's
 * {@link com.eventtick.booking.controller.AdminBookingControllerTest}).
 *
 * <p>No {@code @ExtendWith(MockitoExtension.class)}/{@code @Mock} fields:
 * plain {@link org.mockito.Mockito#mock} calls keep it unambiguous which
 * mock instance {@link #bookingService} was actually built with.
 */
class BookingServiceListAllTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingSeatRepository bookingSeatRepository = mock(BookingSeatRepository.class);
    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final BookingService bookingService =
            new BookingService(bookingRepository, bookingSeatRepository, showSeatRepository);

    @Test
    void listAll_delegatesDirectlyToBookingRepositoryFindAll_withTheGivenPageable() {
        Pageable pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "totalAmount"));
        Booking booking = new Booking();
        Page<Booking> expected = new PageImpl<>(List.of(booking), pageable, 6);
        when(bookingRepository.findAll(pageable)).thenReturn(expected);

        Page<Booking> result = bookingService.listAll(pageable);

        assertThat(result).isSameAs(expected);
        verify(bookingRepository).findAll(pageable);
        // No other repository is touched by a plain list — proves this is
        // exactly findAll(Pageable) and nothing else (no per-row lookups,
        // no seat locking).
        verifyNoMoreInteractions(bookingRepository, bookingSeatRepository, showSeatRepository);
    }

    @Test
    void listAll_returnsAnEmptyPage_whenThereAreNoBookings() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> empty = new PageImpl<>(List.of(), pageable, 0);
        when(bookingRepository.findAll(pageable)).thenReturn(empty);

        Page<Booking> result = bookingService.listAll(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
