package com.eventtick.booking.service;

import com.eventtick.booking.dto.ShowSeatDefinition;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.DuplicateShowSeatException;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates {@code show_seats} inventory rows for an existing show — the
 * administrative counterpart to {@link ShowSeatQueryService}'s read-only
 * seat map and to {@link BookingService}'s status transitions on rows that
 * already exist. This class only ever INSERTs brand-new rows; it never
 * transitions an existing {@code ShowSeat}'s status, so it needs none of
 * {@code BookingService.lockShowSeats}'s {@code SELECT ... FOR UPDATE} —
 * that locking exists for contention on existing rows (§15/§16), not for
 * creating new ones with fresh, server-generated ids.
 *
 * <h2>Why this does not validate {@code showId}/{@code seatId} against catalog-service</h2>
 * They are plain UUID foreign-key references here, exactly as
 * {@link ShowSeat}'s own class Javadoc already documents — booking-service
 * does not maintain a local copy of catalog-service's Show/Seat entities,
 * and no existing booking-service code path (not {@code holdSeats}, not
 * {@code createBooking}) calls catalog-service synchronously to check
 * either exists. Introducing that here would be a new architectural
 * pattern, not a reuse of an existing one (see {@code docs/architecture.md}
 * §51.4/§17). The real safety net is the database itself:
 * {@code fk_show_seats_show}/{@code fk_show_seats_seat}
 * (migration 0008, already in the real PostgreSQL schema) reject a
 * nonexistent show or seat at insert time, exactly as they already do for
 * every other write to this table today.
 */
@Service
public class ShowSeatInventoryService {

    private final ShowSeatRepository showSeatRepository;

    public ShowSeatInventoryService(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    /**
     * Creates one new, {@code AVAILABLE} {@code show_seats} row per seat
     * definition. All-or-nothing within the one database transaction: if
     * any definition is rejected, nothing is persisted.
     *
     * @throws IllegalArgumentException if {@code seats} is empty, or the
     *         same {@code seatId} appears more than once in the request
     * @throws DuplicateShowSeatException if this show already has
     *         inventory for any of the requested seat ids
     */
    @Transactional
    public List<ShowSeat> createSeats(UUID showId, List<ShowSeatDefinition> seats) {
        requireNonEmpty(seats);
        requireNoDuplicatesWithinRequest(seats);
        requireNoExistingInventory(showId, seats);

        List<ShowSeat> newSeats = new ArrayList<>();
        for (ShowSeatDefinition definition : seats) {
            ShowSeat seat = new ShowSeat();
            seat.setShowId(showId);
            seat.setSeatId(definition.seatId());
            seat.setStatus(ShowSeatStatus.AVAILABLE);
            seat.setPrice(definition.price());
            newSeats.add(seat);
        }
        return showSeatRepository.saveAll(newSeats);
    }

    private void requireNonEmpty(List<ShowSeatDefinition> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("At least one seat definition must be specified.");
        }
    }

    /** Mirrors {@code BookingService.requireNonEmpty}'s own duplicate-id check. */
    private void requireNoDuplicatesWithinRequest(List<ShowSeatDefinition> seats) {
        Set<UUID> seatIds = new HashSet<>();
        for (ShowSeatDefinition definition : seats) {
            if (!seatIds.add(definition.seatId())) {
                throw new IllegalArgumentException("Duplicate seatId in request: " + definition.seatId() + ".");
            }
        }
    }

    private void requireNoExistingInventory(UUID showId, List<ShowSeatDefinition> seats) {
        Set<UUID> requestedSeatIds = seats.stream().map(ShowSeatDefinition::seatId).collect(Collectors.toSet());
        List<UUID> alreadyExists = showSeatRepository.findByShowId(showId).stream()
                .map(ShowSeat::getSeatId)
                .filter(requestedSeatIds::contains)
                .toList();
        if (!alreadyExists.isEmpty()) {
            throw new DuplicateShowSeatException(showId, alreadyExists);
        }
    }
}
