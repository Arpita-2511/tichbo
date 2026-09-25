package com.eventtick.user.dto;

/** Response for {@code GET /api/admin/users/stats} (Phase 13, FR-36). */
public record UserStatsResponse(long totalUsers) {
}
