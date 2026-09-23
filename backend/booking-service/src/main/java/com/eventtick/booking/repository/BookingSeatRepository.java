package com.eventtick.booking.repository;

import com.eventtick.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BookingSeat} (the
 * {@code booking_seats} table).
 */
public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    /** Used by {@code BookingService} to find a booking's seat line items. */
    List<BookingSeat> findByBookingId(UUID bookingId);
}
