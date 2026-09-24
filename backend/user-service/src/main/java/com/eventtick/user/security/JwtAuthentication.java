package com.eventtick.user.security;

import com.eventtick.user.entity.UserRole;

import java.util.UUID;

/** The claims extracted from a successfully validated JWT. */
public record JwtAuthentication(UUID userId, String email, UserRole role, String plan) {
}
