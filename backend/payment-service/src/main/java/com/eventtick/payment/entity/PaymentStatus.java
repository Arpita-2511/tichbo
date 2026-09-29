package com.eventtick.payment.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the {@code chk_payments_status} CHECK constraint on the
 * {@code payments} table (see
 * {@code database/migrations/0011_create_payments_table.up.sql}) and the
 * state model approved in Phase 15 Step 1
 * ({@code docs/architecture.md} §25.1):
 *
 * <pre>
 * CREATED ──────► PENDING ──────► SUCCESS   (→ booking CONFIRMED)
 *    │               │
 *    │               ├─────────► FAILED    (→ booking released)
 *    │               │
 *    │               └─────────► EXPIRED   (→ booking released)
 *    │
 *    └─────────────────────────► CANCELLED (→ booking released)
 * </pre>
 *
 * Exactly the six states approved in Step 1 — no more, no less.
 *
 * <p><b>{@code isTerminal()} and {@code isLive()} are independent, not
 * complements.</b> {@code SUCCESS} is both: terminal (no further state
 * transition ever leaves it) <i>and</i> live (it still blocks a new payment
 * attempt for the same booking — the booking was already successfully
 * paid). Only {@code FAILED}/{@code EXPIRED}/{@code CANCELLED} are
 * terminal-and-not-live, which is what actually allows a retry.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    EXPIRED,
    CANCELLED;

    private static final Set<PaymentStatus> TERMINAL = Set.of(SUCCESS, FAILED, EXPIRED, CANCELLED);

    /**
     * Statuses the "at most one live payment per booking" rule (Step 1)
     * treats as still "live" — matches
     * {@code uq_payments_one_active_per_booking}'s partial-index predicate
     * exactly. A booking whose only payment(s) are outside this set may
     * accept a new payment attempt.
     */
    private static final Set<PaymentStatus> LIVE = Set.of(CREATED, PENDING, SUCCESS);

    /**
     * The approved transition table (Phase 15 Step 3,
     * docs/architecture.md §25.3) — the single source of truth for which
     * moves are legal. Centralized here rather than scattered across
     * {@code PaymentService} call sites, per that design's explicit
     * requirement.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(CREATED, EnumSet.of(PENDING, CANCELLED));
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(SUCCESS, FAILED, EXPIRED, CANCELLED));
        ALLOWED_TRANSITIONS.put(SUCCESS, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(EXPIRED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
    }

    /** No transition in this state model ever leaves a terminal status. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Still counts as "in flight or already won" for the one-per-booking rule. */
    public boolean isLive() {
        return LIVE.contains(this);
    }

    /**
     * Whether moving from {@code this} to {@code target} is a legal
     * transition in the approved state model. {@code this == target} is
     * deliberately {@code false} here (not a "transition" at all) —
     * {@code PaymentService} handles same-state idempotent replay as its
     * own explicit case, distinct from an illegal-transition rejection.
     */
    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
