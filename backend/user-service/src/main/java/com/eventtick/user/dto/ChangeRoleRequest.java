package com.eventtick.user.dto;

import com.eventtick.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/admin/users/{userId}/role}. An
 * unrecognized {@code role} string fails Jackson deserialization before
 * {@code @Valid} even runs — see {@code GlobalExceptionHandler}'s
 * {@code HttpMessageNotReadableException} handler, which maps that case to
 * the same {@code 400 VALIDATION_ERROR} shape as every other validation
 * failure.
 */
public record ChangeRoleRequest(@NotNull UserRole role) {
}
