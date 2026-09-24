package com.eventtick.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/auth/register}.
 *
 * <p>No {@code role}/{@code planId}/{@code id}/timestamps — a new
 * registration is always {@code CUSTOMER} on the "Free" plan;
 * {@code AuthService} sets both, never the caller (mirrors how
 * {@code ShowCreateRequest} never accepts a status in catalog-service).
 *
 * <p>{@code @Size(min = 8)} on password matches the frontend's existing
 * validation rule ({@code eventtick/src/pages/Signup.tsx}) — kept simple
 * rather than adding character-class requirements, per "sensible
 * constraints, not arbitrary over-restriction."
 */
public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password
) {
}
