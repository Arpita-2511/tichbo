package com.eventtick.booking.controller;

import com.eventtick.booking.dto.BookingResponse;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service surface for payment-service: read any booking, confirm
 * it, cancel it — with no user identity and no ownership check, because the
 * caller is the system acting on a payment outcome (including from its
 * reconciliation sweep, which has no user token).
 *
 * <p>Deliberately a different path prefix from the public {@code /api/**}
 * endpoints, which enforce BR-07 ownership from the caller's JWT. The API
 * Gateway has no route for {@code /internal/**}, so these are unreachable
 * from clients; see {@code SecurityConfig} for the trust boundary.
 */
@RestController
@RequestMapping("/internal/bookings")
public class InternalBookingController {

    private final BookingService bookingService;

    public InternalBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable UUID bookingId) {
        return toBookingResponse(bookingService.getBooking(bookingId));
    }

    @PostMapping("/{bookingId}/confirm")
    public BookingResponse confirmBooking(@PathVariable UUID bookingId) {
        return toBookingResponse(bookingService.confirmBooking(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId) {
        return toBookingResponse(bookingService.cancelBooking(bookingId));
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.from(booking, bookingService.getBookingSeats(booking.getId()));
    }
}
