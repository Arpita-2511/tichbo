package com.eventtick.booking.controller;

import com.eventtick.booking.dto.CreateShowSeatsRequest;
import com.eventtick.booking.dto.SeatMapItemDto;
import com.eventtick.booking.dto.SeatMapResponse;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.service.ShowSeatInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/admin/shows/{showId}/seats} — creates {@code show_seats}
 * inventory for an existing show, closing the gap
 * {@code docs/architecture.md} §17/§51.4 document: until now, nothing in
 * this codebase created a {@code show_seats} row outside a JUnit test or a
 * manual SQL insert.
 *
 * <p>The administrative counterpart to the customer-facing, read-only
 * {@code GET /api/bookings/shows/{showId}/seats}
 * ({@link BookingController#getSeatMap}) and to
 * {@link AdminShowSeatActivityController}'s read-only seat-activity view —
 * a separate controller, following the same convention as every other
 * admin controller in this service. This class performs no authorization
 * check itself: {@code /api/admin/**} already requires {@code ROLE_ADMIN}
 * both at the API Gateway and at this service's own {@code SecurityConfig}
 * (defense in depth) — nothing new was added to either for this endpoint,
 * the same existing path-based rule already covers it.
 */
@RestController
@RequestMapping("/api/admin/shows")
public class AdminShowSeatInventoryController {

    private final ShowSeatInventoryService showSeatInventoryService;

    public AdminShowSeatInventoryController(ShowSeatInventoryService showSeatInventoryService) {
        this.showSeatInventoryService = showSeatInventoryService;
    }

    /**
     * Response reuses the existing {@link SeatMapResponse}/
     * {@link SeatMapItemDto} shape — exactly what {@code getSeatMap}/
     * {@code holdSeats} already return — rather than inventing a second
     * seat model for the same data.
     */
    @PostMapping("/{showId}/seats")
    public ResponseEntity<SeatMapResponse> createSeats(@PathVariable UUID showId,
                                                        @Valid @RequestBody CreateShowSeatsRequest request) {
        List<ShowSeat> created = showSeatInventoryService.createSeats(showId, request.seats());
        SeatMapResponse response = new SeatMapResponse(showId, created.stream().map(SeatMapItemDto::from).toList());
        return ResponseEntity.created(URI.create("/api/bookings/shows/" + showId + "/seats")).body(response);
    }
}
