package com.eventtick.user.exception;

import java.util.UUID;

/**
 * Thrown when a plan id given to an admin operation (e.g. changing a
 * user's plan) doesn't correspond to a real plan. Mirrors
 * {@link UserNotFoundException} exactly.
 */
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(UUID id) {
        super("Plan not found: " + id);
    }
}
