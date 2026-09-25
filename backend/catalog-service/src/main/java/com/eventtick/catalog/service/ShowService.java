package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.repository.ContentRepository;
import com.eventtick.catalog.repository.ShowRepository;
import com.eventtick.catalog.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence and basic validation for {@link Show} (the {@code shows}
 * table) — the join point between {@link Content} and {@link Venue}.
 * Depends on {@link ContentRepository} and {@link VenueRepository}
 * directly to resolve both, the same repository-composition style used
 * by {@link SeatService}.
 *
 * <p>No relationship to {@code Seat} exists or is added here — per-show
 * seat availability is booking-service's {@code show_seats} table.
 */
@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ContentRepository contentRepository;
    private final VenueRepository venueRepository;

    public ShowService(ShowRepository showRepository, ContentRepository contentRepository,
                        VenueRepository venueRepository) {
        this.showRepository = showRepository;
        this.contentRepository = contentRepository;
        this.venueRepository = venueRepository;
    }

    /**
     * A new show always starts {@code SCHEDULED} — not a caller-supplied
     * parameter, since there's no real decision being made (matching how
     * {@code BookingService.createBooking} always hardcodes a new
     * booking's status to {@code PENDING} rather than accepting it as an
     * argument). Changing status later goes through {@link #update}.
     */
    @Transactional
    public Show create(UUID contentId, UUID venueId, Instant startTime, Instant endTime) {
        Content content = requireContent(contentId);
        Venue venue = requireVenue(venueId);

        Show show = new Show();
        show.setContent(content);
        show.setVenue(venue);
        show.setStartTime(startTime);
        show.setEndTime(endTime);
        show.setStatus(ShowStatus.SCHEDULED);
        validateTimes(show);
        return showRepository.save(show);
    }

    @Transactional(readOnly = true)
    public Show getById(UUID id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Show", id));
    }

    @Transactional(readOnly = true)
    public List<Show> list() {
        return showRepository.findAll();
    }

    /** All shows for a piece of content. */
    @Transactional(readOnly = true)
    public List<Show> listByContent(UUID contentId) {
        return showRepository.findByContentId(contentId);
    }

    /** All shows at a venue. */
    @Transactional(readOnly = true)
    public List<Show> listByVenue(UUID venueId) {
        return showRepository.findByVenueId(venueId);
    }

    /**
     * Full replace of every mutable field, including {@code status} —
     * this is also how a show is cancelled/completed, rather than a
     * separate dedicated method, to keep the service's operation set to
     * the basic create/get/list/update/delete asked for.
     */
    @Transactional
    public Show update(UUID id, UUID contentId, UUID venueId, Instant startTime, Instant endTime,
                        ShowStatus status) {
        Show show = getById(id);
        show.setContent(requireContent(contentId));
        show.setVenue(requireVenue(venueId));
        show.setStartTime(startTime);
        show.setEndTime(endTime);
        if (status == null) {
            throw new IllegalArgumentException("Show status is required.");
        }
        show.setStatus(status);
        validateTimes(show);
        return showRepository.save(show);
    }

    /**
     * Phase 13.6.2: the admin cancel operation
     * ({@code PATCH /api/admin/shows/{id}/cancel}) — sets only
     * {@code status}, leaving {@code content}/{@code venue}/{@code startTime}/
     * {@code endTime} untouched, unlike {@link #update}, which replaces
     * every mutable field. This is deliberately permissive: any current
     * status, including an already-{@code CANCELLED} or {@code COMPLETED}
     * show, transitions to {@code CANCELLED} — no 409 transition rule is
     * enforced yet; that is an intentional, deferred decision for this
     * phase, not an oversight, and {@link #update} is unchanged.
     */
    @Transactional
    public Show cancel(UUID id) {
        Show show = getById(id);
        show.setStatus(ShowStatus.CANCELLED);
        return showRepository.save(show);
    }

    /**
     * Deletes a Show row. Will fail at the database level if
     * booking-service's {@code show_seats.show_id} or
     * {@code bookings.show_id} still reference it (both
     * {@code ON DELETE RESTRICT}) — cross-service constraints this
     * service has no visibility into, enforced only because both
     * databases currently share one PostgreSQL instance.
     */
    @Transactional
    public void delete(UUID id) {
        showRepository.delete(getById(id));
    }

    /**
     * {@code GET /api/admin/content/stats} (Phase 13, FR-36). A live count
     * — no admin-only check here; that is enforced once, at the gateway,
     * the same boundary every other admin-only path relies on. FR-36
     * requires this figure to be sourced live from its owning service, not
     * cached or duplicated, so this reads {@link ShowRepository#count()}
     * directly on every call.
     */
    @Transactional(readOnly = true)
    public long countAll() {
        return showRepository.count();
    }

    private Content requireContent(UUID contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Content", contentId));
    }

    private Venue requireVenue(UUID venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Venue", venueId));
    }

    /** Mirrors chk_shows_end_after_start, giving a clear error before the DB would reject it. */
    private void validateTimes(Show show) {
        if (show.getStartTime() == null || show.getEndTime() == null) {
            throw new IllegalArgumentException("Show start and end time are required.");
        }
        if (!show.getEndTime().isAfter(show.getStartTime())) {
            throw new IllegalArgumentException("Show end time must be after start time.");
        }
    }
}
