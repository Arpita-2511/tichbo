package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ContentRequest;
import com.eventtick.catalog.dto.ContentResponse;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.service.ContentService;
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
 * Phase 13.5.2 ({@link #create}) / FR-38 ({@link #update}, {@link #remove}):
 * the catalog admin operations for Content. A separate, minimal controller
 * — not methods added to {@link ContentController} — so that class (and
 * its five existing {@code /api/catalog/content} routes) is not touched at
 * all: {@code @RequestMapping} paths are always concatenated (class-level +
 * method-level), so hosting both {@code /api/catalog/content} and
 * {@code /api/admin/content} in one class would require restructuring its
 * existing mappings. A second controller avoids that risk entirely.
 *
 * <p>Every method here delegates to the exact same {@link ContentService}
 * methods {@link ContentController} already uses — same validation, same
 * {@link com.eventtick.catalog.exception.GlobalExceptionHandler} error
 * mapping, same {@link ContentRequest}/{@link ContentResponse}. Nothing
 * about content creation/update/removal is duplicated or reimplemented
 * here; this class only adds a second, admin-gated path to reach it. In
 * particular, {@link #remove} is a genuine hard delete — {@link Content}
 * has no status/archived column to toggle instead (see the FR-38
 * inspection report), so there is no soft-delete alternative available
 * without a schema change, which is out of scope here.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself, catalog-service has no Spring
 * Security dependency, and none is added here. Called directly against
 * catalog-service (bypassing the Gateway), every endpoint here accepts any
 * request the same way its {@code /api/catalog/content} counterpart
 * already does; a known, pre-existing, and explicitly out-of-scope
 * condition for this phase (see the Phase 13.5 inspection report).
 */
@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    private final ContentService contentService;

    public AdminContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<ContentResponse> create(@Valid @RequestBody ContentRequest request) {
        Content content = contentService.create(request.type(), request.title(), request.description(),
                request.language(), request.duration(), request.genre(), request.releaseOrEventDate());
        // Same Location convention as ContentController#create: the new
        // resource is still addressed at its one canonical (non-admin) URI.
        return ResponseEntity.created(URI.create("/api/catalog/content/" + content.getId()))
                .body(ContentResponse.from(content));
    }

    /** FR-38: full replace of every mutable field, exactly {@link ContentController#update}'s behavior. */
    @PutMapping("/{id}")
    public ContentResponse update(@PathVariable UUID id, @Valid @RequestBody ContentRequest request) {
        Content content = contentService.update(id, request.type(), request.title(), request.description(),
                request.language(), request.duration(), request.genre(), request.releaseOrEventDate());
        return ContentResponse.from(content);
    }

    /**
     * FR-38: a genuine hard delete, exactly {@link ContentController#delete}'s
     * behavior — not pre-checked here; a Content row still referenced by a
     * Show fails at the database level ({@code fk_shows_content ON DELETE
     * RESTRICT}), surfaced as the existing {@code 409 DATA_INTEGRITY_CONFLICT}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        contentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
