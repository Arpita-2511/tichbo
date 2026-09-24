package com.eventtick.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
