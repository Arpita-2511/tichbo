package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persistence and basic validation for {@link Venue} (the {@code venues}
 * table). No relationships to other Catalog entities on this side —
 * {@link Seat} and {@link com.eventtick.catalog.entity.Show} reference a
 * Venue, not the other way around.
 */
@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public Venue create(String name, String address, String city) {
        Venue venue = new Venue();
        venue.setName(name);
        venue.setAddress(address);
        venue.setCity(city);
        validate(venue);
        return venueRepository.save(venue);
    }

    @Transactional(readOnly = true)
    public Venue getById(UUID id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Venue", id));
    }

    @Transactional(readOnly = true)
    public List<Venue> list() {
        return venueRepository.findAll();
    }

    /** Full replace of every mutable field. */
    @Transactional
    public Venue update(UUID id, String name, String address, String city) {
        Venue venue = getById(id);
        venue.setName(name);
        venue.setAddress(address);
        venue.setCity(city);
        validate(venue);
        return venueRepository.save(venue);
    }

    /**
     * Deletes a Venue row. Will fail at the database level
     * ({@code fk_seats_venue}/{@code fk_shows_venue}, both
     * {@code ON DELETE RESTRICT}) if any Seat or Show still references
     * it, and — since both physical databases currently share one
     * PostgreSQL instance — will also fail if booking-service's
     * {@code show_seats} rows for this venue's seats still exist, even
     * though this service has no knowledge of that table. Not pre-checked
     * here; left to the database constraints.
     */
    @Transactional
    public void delete(UUID id) {
        venueRepository.delete(getById(id));
    }

    private void validate(Venue venue) {
        if (venue.getName() == null || venue.getName().isBlank()) {
            throw new IllegalArgumentException("Venue name is required.");
        }
        if (venue.getAddress() == null || venue.getAddress().isBlank()) {
            throw new IllegalArgumentException("Venue address is required.");
        }
        if (venue.getCity() == null || venue.getCity().isBlank()) {
            throw new IllegalArgumentException("Venue city is required.");
        }
    }
}
