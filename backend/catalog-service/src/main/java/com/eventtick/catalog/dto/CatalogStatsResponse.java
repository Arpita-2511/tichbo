package com.eventtick.catalog.dto;

/** Response for {@code GET /api/admin/content/stats} (Phase 13, FR-36). */
public record CatalogStatsResponse(long totalContent, long totalShows, long totalVenues) {
}
