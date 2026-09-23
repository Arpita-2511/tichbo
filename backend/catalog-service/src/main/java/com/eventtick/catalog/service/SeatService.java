package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Seat;
import com.eventtick.catalog.entity.SeatType;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.repository.SeatRepository;
import com.eventtick.catalog.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persistence and basic validation for {@link Seat} (the {@code seats}
 * table). Depends on {@link VenueRepository} directly (rather than
 * {@link VenueService}) to resolve the owning Venue — the same
 * repository-to-repository composition style {@code BookingService}
 * already uses in booking-service, kept consistent here.
 *
 * <p>No relationship to {@code Show} exists or is added here — a seat's
 * per-show availability is booking-service's {@code show_seats} table,
 * entirely outside this service.
 */
@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final VenueRepository venueRepository;

    public SeatService(SeatRepository seatRepository, VenueRepository venueRepository) {
        this.seatRepository = seatRepository;
        this.venueRepository = venueRepository;
    }

    /**
     * The {@code uq_seats_physical_seat UNIQUE (venue_id, section, "row",
     * seat_number)} constraint is not pre-checked in Java here — doing so
     * via {@link SeatRepository#findByVenueId} would be a check-then-act
     * race that the database constraint has to guard against regardless,
     * so the DB constraint is left as the sole authority, consistent with
     * how FK/CHECK constraints are treated elsewhere in this codebase.
     */
    @Transactional
    public Seat create(UUID venueId, String section, String row, Integer seatNumber, SeatType seatType) {
        Venue venue = requireVenue(venueId);
        Seat seat = new Seat();
        seat.setVenue(venue);
        seat.setSection(section);
        seat.setRow(row);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(seatType);
        validate(seat);
        return seatRepository.save(seat);
    }

    @Transactional(readOnly = true)
    public Seat getById(UUID id) {
        return seatRepository.findById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Seat", id));
    }

    @Transactional(readOnly = true)
    public List<Seat> list() {
        return seatRepository.findAll();
    }

    /** A venue's full seat layout. */
    @Transactional(readOnly = true)
    public List<Seat> listByVenue(UUID venueId) {
        return seatRepository.findByVenueId(venueId);
    }

    /** Full replace of every mutable field, including which venue the seat belongs to. */
    @Transactional
    public Seat update(UUID id, UUID venueId, String section, String row, Integer seatNumber, SeatType seatType) {
        Seat seat = getById(id);
        seat.setVenue(requireVenue(venueId));
        seat.setSection(section);
        seat.setRow(row);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(seatType);
        validate(seat);
        return seatRepository.save(seat);
    }

    /**
     * Deletes a Seat row. Will fail at the database level if
     * booking-service's {@code show_seats.seat_id} still references it
     * ({@code fk_show_seats_seat ON DELETE RESTRICT}) — a cross-service
     * constraint this service has no visibility into, enforced only
     * because both databases currently share one PostgreSQL instance.
     */
    @Transactional
    public void delete(UUID id) {
        seatRepository.delete(getById(id));
    }

    private Venue requireVenue(UUID venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Venue", venueId));
    }

    private void validate(Seat seat) {
        if (seat.getSection() == null || seat.getSection().isBlank()) {
            throw new IllegalArgumentException("Seat section is required.");
        }
        if (seat.getRow() == null || seat.getRow().isBlank()) {
            throw new IllegalArgumentException("Seat row is required.");
        }
        if (seat.getSeatNumber() == null || seat.getSeatNumber() <= 0) {
            throw new IllegalArgumentException("Seat number must be positive.");
        }
        if (seat.getSeatType() == null) {
            throw new IllegalArgumentException("Seat type is required.");
        }
    }
}
