package com.eventtick.booking.dto;

/** Response for {@code GET /api/admin/bookings/stats} (Phase 13, FR-36). */
public record BookingStatsResponse(long totalBookings) {
}
