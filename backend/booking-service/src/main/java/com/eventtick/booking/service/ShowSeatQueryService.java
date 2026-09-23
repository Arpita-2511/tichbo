package com.eventtick.booking.service;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only seat map queries. Never mutates {@code show_seats}, so no
 * locking is involved anywhere in this class.
 *
 * <p>Returns raw {@link ShowSeat} rows only (id, seat id, status, price).
 * Seat labels (section/row/number/type) live in catalog-service's
 * {@code seats} table and are not fetched here — enriching a seat map with
 * that display data means a call to catalog-service's API and is a
 * controller/DTO-layer concern that doesn't exist yet.
 */
@Service
public class ShowSeatQueryService {

    private final ShowSeatRepository showSeatRepository;

    public ShowSeatQueryService(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    /**
     * All show_seats rows for a show, as currently stored. Safe to call at
     * any time — plain read, no transaction semantics beyond the implicit
     * read-only one.
     */
    @Transactional(readOnly = true)
    public List<ShowSeat> getSeatMap(UUID showId) {
        return showSeatRepository.findByShowId(showId);
    }
}
