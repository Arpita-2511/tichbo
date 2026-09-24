package com.eventtick.user.dto;

import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by registration, {@code GET /api/users/me}, and nested inside
 * {@link AuthResponse}. Deliberately excludes {@code passwordHash} and
 * any other internal security field — never add one without checking
 * this class first.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        UUID planId,
        String planName,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        // Reads the plan's NAME, which (unlike its id) needs the Plan to be
        // loaded, not just a lazy placeholder. Callers get that guarantee
        // from UserRepository's @EntityGraph on findByEmail/findById, and
        // from register() setting a real Plan. A User obtained any other
        // way (a new finder without the entity graph) would throw
        // LazyInitializationException here once the session has closed.
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getPlan().getId(),
                user.getPlan().getName(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
