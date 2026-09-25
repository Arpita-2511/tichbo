package com.eventtick.user.exception;

import java.util.UUID;

/**
 * Thrown when an administrator attempts to change their own role via
 * {@code PATCH /api/admin/users/{userId}/role}. The target user id (from
 * the path) and the requesting admin's own id (from their validated JWT,
 * never the request body) are equal. Changing another user's role in
 * either direction (CUSTOMER -> ADMIN, ADMIN -> CUSTOMER) remains allowed
 * — only this self-targeting case is rejected.
 */
public class SelfRoleModificationException extends RuntimeException {

    public SelfRoleModificationException(UUID userId) {
        super("An administrator cannot change their own role: " + userId);
    }
}
