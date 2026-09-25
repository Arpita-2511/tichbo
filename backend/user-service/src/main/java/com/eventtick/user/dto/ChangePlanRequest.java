package com.eventtick.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request body for {@code PATCH /api/admin/users/{userId}/plan}. */
public record ChangePlanRequest(@NotNull UUID planId) {
}
