package com.eventtick.booking.repository;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    /**
     * Bulk-releases expired holds: sets status back to AVAILABLE and
     * clears the hold-ownership columns. Returns the number of rows
     * updated. Called by {@code SeatHoldExpirationScheduler}.
     */
    @Transactional
    @Modifying
    @Query("UPDATE ShowSeat s SET s.status = :available, "
            + "s.holderUserId = null, s.heldAt = null, s.holdExpiresAt = null "
            + "WHERE s.status = :held AND s.holdExpiresAt < :now")
    int releaseExpiredHolds(@Param("available") ShowSeatStatus available,
                            @Param("held") ShowSeatStatus held,
                            @Param("now") Instant now);
}
