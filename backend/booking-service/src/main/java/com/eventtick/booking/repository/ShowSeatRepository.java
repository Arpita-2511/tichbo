package com.eventtick.booking.repository;

import com.eventtick.booking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ShowSeat} (the {@code show_seats}
 * table).
 */
public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    /**
     * Plain, unlocked read of every seat for a show — used for the seat
     * map (viewing only, no state change, no locking needed).
     */
    List<ShowSeat> findByShowId(UUID showId);

    /**
     * Locked read of specific seats, using {@code SELECT ... FOR UPDATE}
     * ({@link LockModeType#PESSIMISTIC_WRITE}). This is the mechanism that
     * makes the AVAILABLE→HELD→BOOKED transitions in
     * {@code com.eventtick.booking.service.BookingService} safe under
     * concurrent requests for the same seat — see that class's Javadoc.
     * Every caller must be inside an active {@code @Transactional} method;
     * the lock is held only for that transaction's duration.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.id in :ids")
    List<ShowSeat> lockAllByIdIn(@Param("ids") Collection<UUID> ids);
}
