package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ContentRequest;
import com.eventtick.catalog.dto.ContentResponse;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.service.ContentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Phase 13.5.2: the first catalog admin operation. A separate, minimal
 * controller — not a method added to {@link ContentController} — so that
 * class (and its five existing {@code /api/catalog/content} routes) is not
 * touched at all: {@code @RequestMapping} paths are always concatenated
 * (class-level + method-level), so hosting both {@code /api/catalog/content}
 * and {@code /api/admin/content} in one class would require restructuring
 * its existing mappings. A second controller avoids that risk entirely.
 *
 * <p>Delegates to the exact same {@link ContentService#create} used by
 * {@link ContentController#create} — same validation, same
 * {@link com.eventtick.catalog.exception.GlobalExceptionHandler} error
 * mapping, same {@link ContentRequest}/{@link ContentResponse}. Nothing
 * about content creation is duplicated or reimplemented here; this class
 * only adds a second, admin-gated path to reach it.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself, catalog-service has no Spring
 * Security dependency, and none is added here. Called directly against
 * catalog-service (bypassing the Gateway), this endpoint accepts any
 * request the same way {@code POST /api/catalog/content} already does; a
 * known, pre-existing, and explicitly out-of-scope condition for this
 * phase (see the Phase 13.5 inspection report).
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
}
