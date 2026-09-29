package com.eventtick.payment.exception;

/**
 * Thrown when a client-supplied {@code idempotencyKey} was already used for
 * a <i>different</i> booking than the one in the current request — see
 * docs/architecture.md §25.1's idempotency design. Never thrown for the
 * same key + same booking (that's a replay, handled as a normal 200, not
 * an error).
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used for a different booking.");
    }
}
