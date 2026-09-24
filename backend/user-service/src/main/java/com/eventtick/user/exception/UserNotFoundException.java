package com.eventtick.user.exception;

import java.util.UUID;

/**
 * Thrown when a user id resolved from a valid, authenticated JWT no
 * longer corresponds to a real user (e.g. the account was deleted after
 * the token was issued). Maps to 404. Not expected in normal operation —
 * this is a defensive check in {@code UserService.getById}, not a
 * reachable outcome of any endpoint accepting a client-supplied id
 * (no such endpoint exists yet beyond {@code /api/users/me}).
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
