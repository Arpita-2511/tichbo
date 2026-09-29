package com.eventtick.payment.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 Step 3: the centralized transition table approved in
 * docs/architecture.md §25.3 — {@link PaymentStatus#canTransitionTo}. Every
 * approved transition and every explicitly-forbidden one from the task's
 * own list is asserted directly, plus a blanket "every terminal state has
 * zero outgoing transitions" check so a future new terminal state can't
 * silently gain one by omission.
 *
 * <p>{@code CREATED -> CANCELLED} is asserted here as a state-machine
 * <i>rule</i> even though no production code path reaches it yet in Step 3
 * — no customer-facing "abandon checkout" endpoint was added, since that's
 * a customer-facing feature decision outside this step's scope (expiration
 * + reconciliation). See {@code docs/architecture.md} §25.3's open
 * questions.
 */
class PaymentStatusTransitionTest {

    // ---- approved transitions ----

    @Test
    void created_canTransitionTo_pending() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.PENDING)).isTrue();
    }

    @Test
    void created_canTransitionTo_cancelled() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
    }

    @Test
    void pending_canTransitionTo_success() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
    }

    @Test
    void pending_canTransitionTo_failed() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void pending_canTransitionTo_expired() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.EXPIRED)).isTrue();
    }

    @Test
    void pending_canTransitionTo_cancelled() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
    }

    // ---- explicitly forbidden (illegal terminal transitions rejected) ----

    @Test
    void success_cannotTransitionTo_failed() {
        assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.FAILED)).isFalse();
    }

    @Test
    void success_cannotTransitionTo_expired() {
        assertThat(PaymentStatus.SUCCESS.canTransitionTo(PaymentStatus.EXPIRED)).isFalse();
    }

    @Test
    void failed_cannotTransitionTo_success() {
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
    }

    @Test
    void expired_cannotTransitionTo_success() {
        assertThat(PaymentStatus.EXPIRED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
    }

    @Test
    void cancelled_cannotTransitionTo_success() {
        assertThat(PaymentStatus.CANCELLED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
    }

    @Test
    void created_cannotTransitionTo_successOrFailedOrExpired_directly() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.SUCCESS)).isFalse();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.EXPIRED)).isFalse();
    }

    // ---- blanket rule: no terminal state has any outgoing transition ----

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED", "EXPIRED", "CANCELLED"})
    void terminalStates_haveNoOutgoingTransitions_toAnyState(PaymentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
        }
    }

    // ---- a state transitioning to itself is not a "transition" at all ----

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void aStateNeverCountsAsATransitionToItself(PaymentStatus status) {
        // PaymentService handles "same status" as an explicit idempotent
        // no-op case, separate from illegal-transition rejection — this
        // just documents that the transition table itself never says yes
        // to a self-loop.
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    // ---- isLive() sanity, since it's easy to confuse with isTerminal() ----

    @Test
    void success_isBothTerminalAndLive_notComplements() {
        assertThat(PaymentStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(PaymentStatus.SUCCESS.isLive()).isTrue();
    }

    @Test
    void failed_isTerminalButNotLive() {
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentStatus.FAILED.isLive()).isFalse();
    }
}
