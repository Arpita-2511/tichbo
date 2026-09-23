package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.VenueRequest;
import com.eventtick.catalog.dto.VenueResponse;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for {@link Venue}. Delegates entirely to
 * {@link VenueService} — no persistence or validation logic here.
 */
@RestController
@RequestMapping("/api/catalog/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
        Venue venue = venueService.create(request.name(), request.address(), request.city());
        return ResponseEntity.created(URI.create("/api/catalog/venues/" + venue.getId()))
                .body(VenueResponse.from(venue));
    }

    @GetMapping
    public List<VenueResponse> list() {
        return venueService.list().stream().map(VenueResponse::from).toList();
    }

    @GetMapping("/{id}")
    public VenueResponse getById(@PathVariable UUID id) {
        return VenueResponse.from(venueService.getById(id));
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        Venue venue = venueService.update(id, request.name(), request.address(), request.city());
        return VenueResponse.from(venue);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        venueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
