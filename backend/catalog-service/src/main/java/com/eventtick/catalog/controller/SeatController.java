package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.SeatRequest;
import com.eventtick.catalog.dto.SeatResponse;
import com.eventtick.catalog.entity.Seat;
import com.eventtick.catalog.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for {@link Seat}. Delegates entirely to
 * {@link SeatService} — no persistence or validation logic here.
 *
 * <p>{@link #list} supports an optional {@code venueId} filter, backed by
 * {@code SeatService.listByVenue} (which already existed, wrapping
 * {@code SeatRepository.findByVenueId} from the repository layer). No new
 * repository method was added to support this controller.
 */
@RestController
@RequestMapping("/api/catalog/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    public ResponseEntity<SeatResponse> create(@Valid @RequestBody SeatRequest request) {
        Seat seat = seatService.create(request.venueId(), request.section(), request.row(),
                request.seatNumber(), request.seatType());
        return ResponseEntity.created(URI.create("/api/catalog/seats/" + seat.getId()))
                .body(SeatResponse.from(seat));
    }

    /** Filtered by venue when {@code venueId} is supplied, otherwise every seat. */
    @GetMapping
    public List<SeatResponse> list(@RequestParam(required = false) UUID venueId) {
        List<Seat> seats = venueId != null ? seatService.listByVenue(venueId) : seatService.list();
        return seats.stream().map(SeatResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SeatResponse getById(@PathVariable UUID id) {
        return SeatResponse.from(seatService.getById(id));
    }

    @PutMapping("/{id}")
    public SeatResponse update(@PathVariable UUID id, @Valid @RequestBody SeatRequest request) {
        Seat seat = seatService.update(id, request.venueId(), request.section(), request.row(),
                request.seatNumber(), request.seatType());
        return SeatResponse.from(seat);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        seatService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
