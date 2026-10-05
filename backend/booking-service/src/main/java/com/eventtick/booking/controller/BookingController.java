package com.eventtick.booking.controller;

import com.eventtick.booking.dto.BookingResponse;
import com.eventtick.booking.exception.BookingAccessDeniedException;
import com.eventtick.booking.dto.CreateBookingRequest;
import com.eventtick.booking.dto.HoldSeatsRequest;
import com.eventtick.booking.dto.HoldSeatsResponse;
import com.eventtick.booking.dto.ReleaseSeatsRequest;
import com.eventtick.booking.dto.ReleaseSeatsResponse;
import com.eventtick.booking.dto.SeatMapItemDto;
import com.eventtick.booking.dto.SeatMapResponse;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.security.CallerIdentity;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ShowSeatQueryService showSeatQueryService;

    public BookingController(BookingService bookingService, ShowSeatQueryService showSeatQueryService) {
        this.bookingService = bookingService;
        this.showSeatQueryService = showSeatQueryService;
    }

    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable UUID showId) {
        List<ShowSeat> seats = showSeatQueryService.getSeatMap(showId);
        return new SeatMapResponse(showId, seats.stream().map(SeatMapItemDto::from).toList());
    }

    /**
     * Hold selected seats. The body's {@code userId} must match the
     * authenticated caller — it is kept for backward compatibility but is
     * not an authority. The authenticated user's identity from the JWT is
     * what the service records as the hold owner.
     */
    @PostMapping("/shows/{showId}/seats/hold")
    public HoldSeatsResponse holdSeats(@PathVariable UUID showId,
                                       @Valid @RequestBody HoldSeatsRequest request,
                                       Authentication authentication) {
        CallerIdentity caller = CallerIdentity.from(authentication);
        if (!request.userId().equals(caller.userId())) {
            throw new BookingAccessDeniedException("You can only hold seats for yourself.");
        }
        List<ShowSeat> held = bookingService.holdSeats(showId, request.showSeatIds(), caller.userId());
        return new HoldSeatsResponse(
                showId,
                held.stream().map(SeatMapItemDto::from).toList(),
                held.isEmpty() ? null : held.get(0).getHoldExpiresAt());
    }

    /**
     * Release held seats. Only seats actually held by the authenticated
     * caller are released; seats held by someone else or already
     * AVAILABLE/BOOKED are left untouched.
     */
    @PostMapping("/shows/{showId}/seats/release")
    public ReleaseSeatsResponse releaseSeats(@PathVariable UUID showId,
                                             @Valid @RequestBody ReleaseSeatsRequest request,
                                             Authentication authentication) {
        CallerIdentity caller = CallerIdentity.from(authentication);
        List<ShowSeat> released = bookingService.releaseHoldForCaller(request.showSeatIds(), caller);
        return new ReleaseSeatsResponse(showId, released.stream().map(SeatMapItemDto::from).toList());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable UUID bookingId, Authentication authentication) {
        return toBookingResponse(bookingService.getBookingForCaller(bookingId, CallerIdentity.from(authentication)));
    }

    /**
     * Create a booking. {@code userId} in the body must match the JWT.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request,
                                                         Authentication authentication,
                                                         @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        CallerIdentity caller = CallerIdentity.from(authentication);
        if (!request.userId().equals(caller.userId())) {
            throw new BookingAccessDeniedException("You can only create bookings for yourself.");
        }
        Booking booking = bookingService.createBooking(request.userId(), request.showId(), request.showSeatIds(), requestId);
        BookingResponse response = toBookingResponse(booking);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.getId())).body(response);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId, Authentication authentication) {
        return toBookingResponse(bookingService.cancelBookingForCaller(bookingId, CallerIdentity.from(authentication)));
    }

    @GetMapping
    public List<BookingResponse> getBookings(@RequestParam(required = false) UUID userId,
                                             Authentication authentication) {
        return bookingService.getBookingsForCaller(CallerIdentity.from(authentication), userId).stream()
                .map(this::toBookingResponse)
                .toList();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.from(booking, bookingService.getBookingSeats(booking.getId()));
    }
}
