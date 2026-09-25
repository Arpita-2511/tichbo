package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.VenueRequest;
import com.eventtick.catalog.dto.VenueResponse;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * FR-38: the admin Venue operations. A separate, minimal controller — not
 * methods added to {@link VenueController} — for the same reason
 * {@link AdminContentController} is separate from
 * {@link com.eventtick.catalog.controller.ContentController}:
 * {@code @RequestMapping} paths are always concatenated (class-level +
 * method-level), so hosting both {@code /api/catalog/venues} and
 * {@code /api/admin/venues} in one class would require restructuring its
 * existing mappings. A second controller avoids that risk entirely —
 * {@link VenueController} and its five existing routes are not touched.
 *
 * <p>{@link #create}, {@link #update}, and {@link #remove} delegate to the
 * exact same {@link VenueService#create}/{@link VenueService#update}/
 * {@link VenueService#delete} used by {@link VenueController} — same
 * validation, same {@link com.eventtick.catalog.exception.GlobalExceptionHandler}
 * error mapping, same {@link VenueRequest}/{@link VenueResponse}. Nothing
 * about venue creation/update/removal is duplicated or reimplemented here;
 * this class only adds a second, admin-gated path to reach them —
 * mirroring {@link AdminContentController}, which reuses
 * {@code ContentService}'s methods unchanged. {@code venues} has no unique
 * constraint of any kind (see the migration), so — unlike, say, a
 * duplicate-email registration — there is no conflict/409 behavior to
 * preserve here; none is invented. {@link #remove} is a genuine hard
 * delete, not pre-checked here — the existing database FK constraints
 * (`fk_seats_venue`/`fk_shows_venue`, both {@code ON DELETE RESTRICT}) are
 * the sole source of truth, surfaced via the existing
 * {@code DataIntegrityViolationException} handler; no soft-delete/status
 * mechanism is introduced.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself, catalog-service has no Spring
 * Security dependency, and none is added here. Called directly against
 * catalog-service (bypassing the Gateway), this endpoint accepts any
 * request the same way {@code POST /api/catalog/venues} already does; the
 * same pre-existing, disclosed condition as {@link AdminContentController}.
 */
@RestController
@RequestMapping("/api/admin/venues")
public class AdminVenueController {

    private final VenueService venueService;

    public AdminVenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
        Venue venue = venueService.create(request.name(), request.address(), request.city());
        // Same Location convention as VenueController#create: the new
        // resource is still addressed at its one canonical (non-admin) URI.
        return ResponseEntity.created(URI.create("/api/catalog/venues/" + venue.getId()))
                .body(VenueResponse.from(venue));
    }

    /** FR-38: exactly {@link VenueController#update}'s behavior, reused unchanged. */
    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        Venue venue = venueService.update(id, request.name(), request.address(), request.city());
        return VenueResponse.from(venue);
    }

    /**
     * FR-38: exactly {@link VenueController#delete}'s behavior, reused
     * unchanged. A genuine hard delete — still referenced by a Seat or Show
     * fails at the database level, surfaced as the existing
     * {@code 409 DATA_INTEGRITY_CONFLICT}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        venueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
