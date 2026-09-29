package com.eventtick.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Eventtick Booking Service.
 *
 * <p>Owns show-specific seat availability, bookings, and their seat line
 * items (the {@code show_seats}, {@code bookings}, and
 * {@code booking_seats} tables — see {@code database/migrations/0008}–
 * {@code 0010}). This service will eventually implement the transactional
 * seat-locking logic (row-level locking on its own {@code show_seats}
 * table) needed to prevent double booking.
 *
 * <p>{@code @EnableScheduling} (Phase 16 Step 2): required for
 * {@code OutboxPublisher}'s {@code @Scheduled} sweep — the same,
 * already-established pattern {@code PaymentLifecycleScheduler} uses in
 * payment-service.
 */
@SpringBootApplication
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
