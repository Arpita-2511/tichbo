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
    public BookingResponse getBooking(@PathVariable UUID bookingId, Authentication authentication) {
        return toBookingResponse(bookingService.getBookingForCaller(bookingId, CallerIdentity.from(authentication)));
    }

    /**
     * 4. Create a booking. {@code userId} stays in the body for API
     * compatibility, but it is only accepted when it matches the
     * authenticated caller — it is not an authority for anything.
     *
     * <p>{@code X-Request-ID} (Phase 16 Step 2): the Gateway already sets
     * this on every routed request ({@code RequestIdWebFilter}), even
     * though booking-service has never read it until now. Reused as the
     * outbox event's {@code correlationId} (docs/architecture.md §47.5/§48)
     * rather than inventing a second, unrelated request-id mechanism.
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

    /*
     * There is deliberately no public "confirm a booking" endpoint (5).
     * Confirmation is a payment outcome, not a customer or admin action:
     * only payment-service confirms, after its payment reaches SUCCESS, via
     * POST /internal/bookings/{id}/confirm. A public one let any signed-in
     * customer confirm an unpaid booking. See SecurityConfig, which also
     * denies the old path outright.
     */

    /**
     * 6. Cancel a booking — owner only. Any request body is ignored: the
     * caller's identity comes from the JWT.
     */
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId, Authentication authentication) {
        return toBookingResponse(bookingService.cancelBookingForCaller(bookingId, CallerIdentity.from(authentication)));
    }

    /**
     * 7. List bookings. A customer gets their own; an administrator gets the
     * named user's, or all when {@code userId} is omitted. See
     * {@link BookingService#getBookingsForCaller}.
     */
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
