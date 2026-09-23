package com.eventtick.catalog.repository;

import com.eventtick.catalog.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Venue} (the {@code venues}
 * table). No custom query methods yet — the reverse lookups implied by
 * {@code venue_id} foreign keys ("seats for this venue", "shows at this
 * venue") live on {@link SeatRepository} and {@link ShowRepository}
 * respectively, not here.
 */
public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
