package com.eventtick.booking.service;

import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Periodic sweep that releases expired seat holds back to AVAILABLE.
 * Complements the lazy expiration in {@link BookingService}: the lazy
 * path handles seats touched by an incoming request, while this
 * scheduler frees orphaned holds that no subsequent request ever
 * touched (e.g. a user who abandoned checkout entirely).
 *
 * <p>Interval is configured via {@code booking.seat-hold.cleanup-interval-ms}
 * (default 60 000 ms / 1 minute). In tests this is set to a very large
 * value so the sweep never fires automatically.
 */
@Component
public class SeatHoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldExpirationScheduler.class);

    private final ShowSeatRepository showSeatRepository;

    public SeatHoldExpirationScheduler(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedDelayString = "${booking.seat-hold.cleanup-interval-ms}")
    public void releaseExpiredHolds() {
        int released = showSeatRepository.releaseExpiredHolds(
                ShowSeatStatus.AVAILABLE, ShowSeatStatus.HELD, Instant.now());
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
