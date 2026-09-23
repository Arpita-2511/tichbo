package com.eventtick.catalog.repository;

import com.eventtick.catalog.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Seat} (the {@code seats} table).
 */
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * A venue's full seat layout. Required because {@code Venue} has no
     * inverse {@code @OneToMany} to {@code Seat} (a deliberate choice in
     * the entity layer — see {@code Venue}'s class Javadoc); this is the
     * only way to answer "what are this venue's seats", a basic operation
     * needed to build any seat map.
     */
    List<Seat> findByVenueId(UUID venueId);
}
