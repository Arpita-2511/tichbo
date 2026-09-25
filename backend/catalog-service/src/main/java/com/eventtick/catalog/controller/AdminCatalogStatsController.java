package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.CatalogStatsResponse;
import com.eventtick.catalog.service.ContentService;
import com.eventtick.catalog.service.ShowService;
import com.eventtick.catalog.service.VenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 13, FR-36: {@code GET /api/admin/content/stats} — the
 * catalog-service portion of Admin Overview and Statistics. A separate,
 * minimal controller — not a method added to {@link AdminContentController}
 * — because this figure spans all three catalog entities (Content, Show,
 * Venue), not just Content; the URL happens to live under
 * {@code /api/admin/content} (per the approved endpoint contract), but the
 * data it returns is not Content-specific.
 *
 * <p>Delegates to the exact same {@link ContentService#countAll}/
 * {@link ShowService#countAll}/{@link VenueService#countAll} — each a
 * one-line {@code repository.count()}, inherited free from
 * {@code JpaRepository}. No new repository query, no caching, no
 * aggregation beyond combining three already-independent counts into one
 * response; each figure is still read live from its owning table on every
 * call, per FR-36's requirement that statistics not be duplicated/cached.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself, the same pre-existing, disclosed
 * condition as every other admin controller in this service.
 */
@RestController
public class AdminCatalogStatsController {

    private final ContentService contentService;
    private final ShowService showService;
    private final VenueService venueService;

    public AdminCatalogStatsController(ContentService contentService, ShowService showService,
                                        VenueService venueService) {
        this.contentService = contentService;
        this.showService = showService;
        this.venueService = venueService;
    }

    @GetMapping("/api/admin/content/stats")
    public CatalogStatsResponse stats() {
        return new CatalogStatsResponse(contentService.countAll(), showService.countAll(), venueService.countAll());
    }
}
