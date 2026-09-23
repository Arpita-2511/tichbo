package com.eventtick.booking.repository;

import com.eventtick.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Booking} (the {@code bookings}
 * table).
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Used by {@code BookingService.getBookingsForUser}. Not paginated yet. */
    List<Booking> findByUserId(UUID userId);
}
