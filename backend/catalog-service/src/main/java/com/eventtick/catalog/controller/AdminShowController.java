package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ShowCreateRequest;
import com.eventtick.catalog.dto.ShowResponse;
import com.eventtick.catalog.dto.ShowUpdateRequest;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Phase 13.6.2 ({@link #cancel}) / FR-38 ({@link #create}, {@link #update},
 * {@link #remove}): the admin Show operations. A separate, minimal
 * controller — not methods added to {@link ShowController} — for the same
 * reason {@link AdminContentController} is separate from
 * {@link com.eventtick.catalog.controller.ContentController}:
 * {@code @RequestMapping} paths are always concatenated (class-level +
 * method-level), so hosting both {@code /api/catalog/shows} and
 * {@code /api/admin/shows} in one class would require restructuring its
 * existing mappings. A second controller avoids that risk entirely —
 * {@link ShowController} and its five existing routes are not touched.
 *
 * <p>{@link #create}, {@link #update}, and {@link #remove} delegate to the
 * exact same {@link ShowService#create}/{@link ShowService#update}/
 * {@link ShowService#delete} used by {@link ShowController} — same
 * validation, same {@link com.eventtick.catalog.exception.GlobalExceptionHandler}
 * error mapping, same {@link ShowCreateRequest}/{@link ShowUpdateRequest}/
 * {@link ShowResponse}; nothing about show creation/update/removal is
 * duplicated or reimplemented here, mirroring how
 * {@link AdminContentController} reuses {@code ContentService}'s methods
 * unchanged. {@link #update} is a full replace of every mutable field,
 * including {@code status} — the same semantics {@code ShowService.update}
 * has always had, unchanged here (no new transition rule). {@link #cancel}
 * remains the narrower, status-only alternative via
 * {@link ShowService#cancel} — see that method's own Javadoc.
 * {@link #remove} is a genuine hard delete, not pre-checked here — the
 * existing database FK constraints (booking-service's {@code show_seats}/
 * {@code bookings}, both {@code ON DELETE RESTRICT}) are the sole source of
 * truth, surfaced via the existing {@code DataIntegrityViolationException}
 * handler; no soft-delete/status mechanism is introduced.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself; catalog-service has no Spring
 * Security dependency, and none is added here. The same pre-existing,
 * disclosed condition as {@link AdminContentController}: called directly
 * against catalog-service (bypassing the Gateway), every endpoint here
 * accepts any request the same way its {@code /api/catalog/shows}
 * counterpart already does.
 */
@RestController
@RequestMapping("/api/admin/shows")
public class AdminShowController {

    private final ShowService showService;

    public AdminShowController(ShowService showService) {
        this.showService = showService;
    }

    /** FR-38: exactly {@link ShowController#create}'s behavior, reused unchanged. */
    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowCreateRequest request) {
        Show show = showService.create(request.contentId(), request.venueId(),
                request.startTime(), request.endTime());
        // Same Location convention as ShowController#create: the new
        // resource is still addressed at its one canonical (non-admin) URI.
        return ResponseEntity.created(URI.create("/api/catalog/shows/" + show.getId()))
                .body(ShowResponse.from(show));
    }

    /** FR-38: exactly {@link ShowController#update}'s behavior, reused unchanged. */
    @PutMapping("/{id}")
    public ShowResponse update(@PathVariable UUID id, @Valid @RequestBody ShowUpdateRequest request) {
        Show show = showService.update(id, request.contentId(), request.venueId(),
                request.startTime(), request.endTime(), request.status());
        return ShowResponse.from(show);
    }

    @PatchMapping("/{id}/cancel")
    public ShowResponse cancel(@PathVariable UUID id) {
        Show show = showService.cancel(id);
        return ShowResponse.from(show);
    }

    /**
     * FR-38: exactly {@link ShowController#delete}'s behavior, reused
     * unchanged. A genuine hard delete — still referenced by
     * booking-service's {@code show_seats}/{@code bookings} fails at the
     * database level, surfaced as the existing
     * {@code 409 DATA_INTEGRITY_CONFLICT}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        showService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
