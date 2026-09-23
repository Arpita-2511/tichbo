package com.eventtick.booking.controller;

import com.eventtick.booking.dto.BookingResponse;
import com.eventtick.booking.dto.CancelBookingRequest;
import com.eventtick.booking.dto.CreateBookingRequest;
import com.eventtick.booking.dto.HoldSeatsRequest;
import com.eventtick.booking.dto.HoldSeatsResponse;
import com.eventtick.booking.dto.ReleaseSeatsRequest;
import com.eventtick.booking.dto.ReleaseSeatsResponse;
import com.eventtick.booking.dto.SeatMapItemDto;
import com.eventtick.booking.dto.SeatMapResponse;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for the approved Booking Service API contract. Every
 * method here delegates directly to {@link BookingService} or
 * {@link ShowSeatQueryService} and maps the result to a DTO — no booking
 * rules live in this class. A single controller covers all seven
 * endpoints (rather than a separate {@code ShowSeatController}) because
 * the approved contract nests the seat-map/hold/release endpoints under
 * {@code /api/bookings/shows/{showId}/...}, i.e. under this same
 * resource, not a separate top-level one.
 *
 * <p>Exceptions thrown by the service layer are not handled here — see
 * {@code com.eventtick.booking.exception.GlobalExceptionHandler} for the
 * exception-to-HTTP-status mapping.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ShowSeatQueryService showSeatQueryService;

    public BookingController(BookingService bookingService, ShowSeatQueryService showSeatQueryService) {
        this.bookingService = bookingService;
        this.showSeatQueryService = showSeatQueryService;
    }

    /** 1. Get seat map for a show. */
    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable UUID showId) {
        List<ShowSeat> seats = showSeatQueryService.getSeatMap(showId);
        return new SeatMapResponse(showId, seats.stream().map(SeatMapItemDto::from).toList());
    }

    /** 2. Hold selected seats. */
    @PostMapping("/shows/{showId}/seats/hold")
    public HoldSeatsResponse holdSeats(@PathVariable UUID showId, @Valid @RequestBody HoldSeatsRequest request) {
        List<ShowSeat> held = bookingService.holdSeats(showId, request.showSeatIds(), request.userId());
        return new HoldSeatsResponse(showId, held.stream().map(SeatMapItemDto::from).toList());
    }

    /** 3. Release held seats. */
    @PostMapping("/shows/{showId}/seats/release")
    public ReleaseSeatsResponse releaseSeats(@PathVariable UUID showId, @Valid @RequestBody ReleaseSeatsRequest request) {
        List<ShowSeat> released = bookingService.releaseHold(request.showSeatIds());
        return new ReleaseSeatsResponse(showId, released.stream().map(SeatMapItemDto::from).toList());
    }

    /** Get a single booking by id (fixes the Location header from #4 actually resolving). */
    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable UUID bookingId) {
        return toBookingResponse(bookingService.getBooking(bookingId));
    }

    /** 4. Create a booking. */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.createBooking(request.userId(), request.showId(), request.showSeatIds());
        BookingResponse response = toBookingResponse(booking);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.getId())).body(response);
    }

    /** 5. Confirm a booking. */
    @PostMapping("/{bookingId}/confirm")
    public BookingResponse confirmBooking(@PathVariable UUID bookingId) {
        return toBookingResponse(bookingService.confirmBooking(bookingId));
    }

    /** 6. Cancel a booking. */
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId, @Valid @RequestBody CancelBookingRequest request) {
        return toBookingResponse(bookingService.cancelBooking(bookingId, request.requestingUserId()));
    }

    /** 7. Get bookings for a user. */
    @GetMapping
    public List<BookingResponse> getBookingsForUser(@RequestParam UUID userId) {
        return bookingService.getBookingsForUser(userId).stream()
                .map(this::toBookingResponse)
                .toList();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.from(booking, bookingService.getBookingSeats(booking.getId()));
    }
}
