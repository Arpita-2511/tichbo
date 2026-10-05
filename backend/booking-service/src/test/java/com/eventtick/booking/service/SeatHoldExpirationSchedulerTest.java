package com.eventtick.booking.service;

import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 22: verifies the scheduler delegates to
 * {@link ShowSeatRepository#releaseExpiredHolds}.
 */
class SeatHoldExpirationSchedulerTest {

    private final ShowSeatRepository showSeatRepository = mock(ShowSeatRepository.class);
    private final SeatHoldExpirationScheduler scheduler = new SeatHoldExpirationScheduler(showSeatRepository);

    @Test
    void releaseExpiredHolds_delegatesToRepository() {
        when(showSeatRepository.releaseExpiredHolds(any(), any(), any())).thenReturn(3);

        scheduler.releaseExpiredHolds();

        verify(showSeatRepository).releaseExpiredHolds(
                eq(ShowSeatStatus.AVAILABLE),
                eq(ShowSeatStatus.HELD),
                any(Instant.class));
    }

    @Test
    void releaseExpiredHolds_noExpiredHolds_stillCallsRepository() {
        when(showSeatRepository.releaseExpiredHolds(any(), any(), any())).thenReturn(0);

        scheduler.releaseExpiredHolds();

        verify(showSeatRepository).releaseExpiredHolds(
                eq(ShowSeatStatus.AVAILABLE),
                eq(ShowSeatStatus.HELD),
                any(Instant.class));
    }
}
