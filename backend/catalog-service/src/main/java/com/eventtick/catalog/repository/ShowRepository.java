package com.eventtick.catalog.repository;

import com.eventtick.catalog.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Show} (the {@code shows} table).
 */
public interface ShowRepository extends JpaRepository<Show, UUID> {

    /**
     * All shows for a piece of content. Required because {@code Content}
     * has no inverse {@code @OneToMany} to {@code Show} (a deliberate
     * choice in the entity layer — see {@code Content}'s class Javadoc);
     * this is the only way to answer "what shows exist for this movie",
     * a basic browsing operation.
     */
    List<Show> findByContentId(UUID contentId);

    /**
     * All shows at a venue. Required for the same reason as
     * {@link #findByContentId} — {@code Venue} has no inverse collection
     * to {@code Show} either.
     */
    List<Show> findByVenueId(UUID venueId);
}
