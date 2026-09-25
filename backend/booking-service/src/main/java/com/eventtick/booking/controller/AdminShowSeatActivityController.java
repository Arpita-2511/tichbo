package com.eventtick.booking.controller;

import com.eventtick.booking.dto.SeatMapItemDto;
import com.eventtick.booking.dto.SeatMapResponse;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.service.ShowSeatQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Phase 13.7.2: {@code GET /api/admin/shows/{showId}/seat-activity} — an
 * admin view of a show's current seat/hold/booking state (FR-39). A
 * separate controller from {@link BookingController}, following the same
 * convention as {@code AdminBookingController} (Phase 13.7.1) and
 * catalog-service's admin controllers: this class performs no authorization
 * check itself — that is enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3).
 * booking-service has no Spring Security dependency.
 *
 * <p>Delegates to the existing {@link ShowSeatQueryService#getSeatMap},
 * exactly the query {@link BookingController#getSeatMap} already uses for
 * the customer-facing {@code GET /api/bookings/shows/{showId}/seats} —
 * same read, same response shape ({@link SeatMapResponse}/
 * {@link SeatMapItemDto}), no new repository query, no new DTO. That shape
 * only carries {@code showSeatId}/{@code seatId}/{@code status}/
 * {@code price} — it does not expose which booking (if any) currently
 * holds a seat, since {@link ShowSeat} itself carries no such reference;
 * inventing that field here would be scope beyond what the existing query
 * provides.
 */
@RestController
@RequestMapping("/api/admin/shows")
public class AdminShowSeatActivityController {

    private final ShowSeatQueryService showSeatQueryService;

    public AdminShowSeatActivityController(ShowSeatQueryService showSeatQueryService) {
        this.showSeatQueryService = showSeatQueryService;
    }

    @GetMapping("/{showId}/seat-activity")
    public SeatMapResponse getSeatActivity(@PathVariable UUID showId) {
        List<ShowSeat> seats = showSeatQueryService.getSeatMap(showId);
        return new SeatMapResponse(showId, seats.stream().map(SeatMapItemDto::from).toList());
    }
}
