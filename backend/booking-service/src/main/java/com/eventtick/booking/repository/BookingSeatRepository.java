package com.eventtick.booking.repository;

import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BookingSeat} (the
 * {@code booking_seats} table).
 */
public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    /** Used by {@code BookingService} to find a booking's seat line items. */
    List<BookingSeat> findByBookingId(UUID bookingId);

    /** Which of these show seats already belong to a booking in one of {@code statuses}. */
    @Query("SELECT bs.showSeat.id FROM BookingSeat bs "
            + "WHERE bs.showSeat.id IN :showSeatIds AND bs.booking.status IN :statuses")
    List<UUID> findShowSeatIdsWithBookingInStatus(@Param("showSeatIds") Collection<UUID> showSeatIds,
                                                  @Param("statuses") Collection<BookingStatus> statuses);
}
