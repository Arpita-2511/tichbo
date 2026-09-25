package com.eventtick.booking.controller;

import com.eventtick.booking.dto.BookingResponse;
import com.eventtick.booking.dto.BookingStatsResponse;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.service.BookingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 13.7.1 ({@link #listBookings}) / FR-36 ({@link #stats}): the admin
 * Booking read operations. A separate controller from {@link BookingController},
 * following the same convention as catalog-service's
 * {@code AdminContentController}/{@code AdminShowController} and
 * user-service's admin controllers: this class performs no authorization
 * check itself — that is enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3).
 * booking-service has no Spring Security dependency.
 *
 * <p><b>{@link #stats} (FR-36):</b> a live count, not a cached/duplicated
 * figure — the Admin Dashboard's overview statistics are required to be
 * sourced from the service that owns each figure, and booking counts are
 * owned by this service.
 */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Standard {@code page}/{@code size}/{@code sort} query parameters via
     * {@link Pageable}; most-recently-created bookings first by default
     * (mirrors {@code GET /api/admin/users}'s default). No filtering by
     * status or show yet — out of scope for this phase.
     */
    @GetMapping
    public Page<BookingResponse> listBookings(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.listAll(pageable).map(this::toBookingResponse);
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.from(booking, bookingService.getBookingSeats(booking.getId()));
    }

    @GetMapping("/stats")
    public BookingStatsResponse stats() {
        return new BookingStatsResponse(bookingService.countAll());
    }
}
