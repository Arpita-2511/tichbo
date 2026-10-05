package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.BookingAccessDeniedException;
import com.eventtick.booking.exception.BookingNotFoundException;
import com.eventtick.booking.exception.InvalidBookingStateException;
import com.eventtick.booking.exception.InvalidSeatStateException;
import com.eventtick.booking.exception.SeatShowMismatchException;
import com.eventtick.booking.event.BookingCreatedPayload;
import com.eventtick.booking.event.EventTopics;
import com.eventtick.booking.outbox.OutboxService;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.repository.ShowSeatRepository;
import com.eventtick.booking.security.CallerIdentity;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seat-hold and booking lifecycle: {@code AVAILABLE -> HELD -> BOOKED} on
 * {@code show_seats}, and {@code PENDING -> CONFIRMED / CANCELLED} on
 * {@code bookings}.
 *
 * <h2>Concurrency</h2>
 * Every method that transitions a {@code ShowSeat}'s status
 * ({@link #holdSeats}, {@link #releaseHold}, the seat transitions inside
 * {@link #confirmBooking} and {@link #cancelBooking}) goes through
 * {@link #lockShowSeats}, which uses
 * {@link ShowSeatRepository#lockAllByIdIn} — a {@code SELECT ... FOR
 * UPDATE} row lock — inside an {@code @Transactional} method.
 *
 * <h2>Hold ownership (Phase 22)</h2>
 * {@link #holdSeats} records <i>who</i> holds a seat (via
 * {@code holderUserId}) and <i>when</i> that hold expires (via
 * {@code holdExpiresAt}, computed from the configurable
 * {@code booking.seat-hold.duration}). {@link #createBooking} verifies
 * that the requesting user owns the hold and that it has not expired.
 * {@link #releaseHoldForCaller} enforces ownership on the public release
 * endpoint.
 *
 * <h2>Hold expiration (Phase 22)</h2>
 * Two mechanisms ensure expired holds are cleaned up:
 * <ol>
 *   <li><b>Lazy expiration:</b> {@link #expireStaleHolds} runs after
 *       locking seats in any write path, releasing expired holds inline
 *       so no stale HELD seat blocks a subsequent hold or booking.</li>
 *   <li><b>Scheduled cleanup:</b>
 *       {@link SeatHoldExpirationScheduler} periodically sweeps
 *       {@link ShowSeatRepository#releaseExpiredHolds} to free orphaned
 *       holds that no subsequent request ever touched.</li>
 * </ol>
 *
 * <h2>Booking ownership (BR-07)</h2>
 * Reading, listing and cancelling a booking on behalf of a signed-in user
 * go through {@link #getBookingForCaller}, {@link #getBookingsForCaller} and
 * {@link #cancelBookingForCaller}, which take the caller's identity from the
 * validated JWT. The plain {@link #getBooking}/{@link #cancelBooking}/
 * {@link #confirmBooking} carry no ownership check and are reached only by
 * payment-service through {@code /internal/**}.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final OutboxService outboxService;
    private final Duration holdDuration;

    public BookingService(BookingRepository bookingRepository,
                           BookingSeatRepository bookingSeatRepository,
                           ShowSeatRepository showSeatRepository,
                           OutboxService outboxService,
                           @Value("${booking.seat-hold.duration}") Duration holdDuration) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showSeatRepository = showSeatRepository;
        this.outboxService = outboxService;
        this.holdDuration = holdDuration;
    }

    /**
     * Transitions the given seats {@code AVAILABLE -> HELD} for one show,
     * recording the holder's identity and the hold expiration timestamp.
     * Lazy-expires any stale holds on the requested seats first, so an
     * expired hold on a seat does not block a new one.
     *
     * @throws SeatShowMismatchException if a showSeatId doesn't belong to {@code showId}
     * @throws InvalidSeatStateException if any seat isn't currently AVAILABLE (after lazy expiration)
     */
    @Transactional
    public List<ShowSeat> holdSeats(UUID showId, List<UUID> showSeatIds, UUID userId) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        expireStaleHolds(seats);
        validateSeatsBelongToShow(seats, showId);
        validateSeatsInStatus(seats, ShowSeatStatus.AVAILABLE);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(holdDuration);
        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeatStatus.HELD);
            seat.setHolderUserId(userId);
            seat.setHeldAt(now);
            seat.setHoldExpiresAt(expiresAt);
        }
        return seats;
    }

    /**
     * System-level, best-effort release: any of the given seats currently
     * {@code HELD} are reverted to {@code AVAILABLE}; seats already
     * {@code AVAILABLE} or already {@code BOOKED} are left untouched.
     * No ownership check — used by internal/system callers.
     */
    @Transactional
    public List<ShowSeat> releaseHold(List<UUID> showSeatIds) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == ShowSeatStatus.HELD) {
                clearHoldFields(seat);
            }
        }
        return seats;
    }

    /**
     * Caller-owned release (Phase 22): only seats held by the caller are
     * released. Seats held by a different user are left untouched (not an
     * error — the caller may have lost the hold to expiration and another
     * user re-held it). Seats already AVAILABLE or BOOKED are also left
     * untouched.
     */
    @Transactional
    public List<ShowSeat> releaseHoldForCaller(List<UUID> showSeatIds, CallerIdentity caller) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == ShowSeatStatus.HELD
                    && caller.userId().equals(seat.getHolderUserId())) {
                clearHoldFields(seat);
            }
        }
        return seats;
    }

    /**
     * Records a new {@code PENDING} booking for seats that must already be
     * {@code HELD} by the requesting user and not expired.
     *
     * <p>Phase 22: after locking, lazy-expires stale holds, then verifies
     * each seat is HELD by {@code userId} specifically. An expired or
     * foreign hold throws {@link InvalidSeatStateException}.
     *
     * @param correlationId ties the resulting event back to the request
     * @throws SeatShowMismatchException if a showSeatId doesn't belong to {@code showId}
     * @throws InvalidSeatStateException if any seat isn't HELD by this user
     */
    @Transactional
    public Booking createBooking(UUID userId, UUID showId, List<UUID> showSeatIds, String correlationId) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        expireStaleHolds(seats);
        validateSeatsBelongToShow(seats, showId);
        validateSeatsHeldByUser(seats, userId);

        List<UUID> alreadyBooked = bookingSeatRepository.findShowSeatIdsWithBookingInStatus(
                showSeatIds, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        if (!alreadyBooked.isEmpty()) {
            throw new InvalidSeatStateException(alreadyBooked.get(0), "already has an active booking");
        }

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowId(showId);
        booking.setStatus(BookingStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (ShowSeat seat : seats) {
            total = total.add(seat.getPrice());
        }
        booking.setTotalAmount(total);
        Booking savedBooking = bookingRepository.save(booking);

        List<BookingSeat> lineItems = new ArrayList<>();
        for (ShowSeat seat : seats) {
            BookingSeat lineItem = new BookingSeat();
            lineItem.setBooking(savedBooking);
            lineItem.setShowSeat(seat);
            lineItem.setPriceAtBooking(seat.getPrice());
            lineItems.add(lineItem);
        }
        bookingSeatRepository.saveAll(lineItems);

        BookingCreatedPayload payload = new BookingCreatedPayload(
                savedBooking.getId(), userId, showId, showSeatIds, total);
        outboxService.record("BookingCreated", 1, "Booking", savedBooking.getId(),
                EventTopics.BOOKING, payload, correlationId, null);

        return savedBooking;
    }

    /**
     * Confirms a {@code PENDING} booking: its seats transition
     * {@code HELD -> BOOKED} and the booking becomes {@code CONFIRMED}.
     * Clears the hold-ownership fields since the seat is now permanently
     * booked.
     *
     * <p><b>Idempotent on an already-{@code CONFIRMED} booking</b>.
     *
     * @throws BookingNotFoundException if bookingId doesn't exist
     * @throws InvalidBookingStateException if the booking is neither PENDING nor already CONFIRMED
     * @throws InvalidSeatStateException if any of its seats aren't HELD
     */
    @Transactional
    public Booking confirmBooking(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), "confirmed");
        }

        List<ShowSeat> seats = lockSeatsForBooking(bookingId);
        validateSeatsInStatus(seats, ShowSeatStatus.HELD);
        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeatStatus.BOOKED);
            seat.setHolderUserId(null);
            seat.setHeldAt(null);
            seat.setHoldExpiresAt(null);
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }

    /**
     * Cancels a {@code PENDING} or {@code CONFIRMED} booking; its seats
     * are released back to {@code AVAILABLE}. Clears hold-ownership
     * fields.
     *
     * <p><b>Idempotent on an already-{@code CANCELLED} booking</b>.
     * <p><b>No ownership check</b> — system-level cancel for payment-service.
     */
    @Transactional
    public Booking cancelBooking(UUID bookingId) {
        return cancelExisting(requireBooking(bookingId));
    }

    /**
     * Customer-initiated cancel (BR-07): only the booking's owner may cancel it.
     */
    @Transactional
    public Booking cancelBookingForCaller(UUID bookingId, CallerIdentity caller) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getUserId().equals(caller.userId())) {
            throw new BookingAccessDeniedException(bookingId);
        }
        return cancelExisting(booking);
    }

    private Booking cancelExisting(Booking booking) {
        UUID bookingId = booking.getId();
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), "cancelled");
        }

        List<ShowSeat> seats = lockSeatsForBooking(bookingId);
        for (ShowSeat seat : seats) {
            clearHoldFields(seat);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForCaller(CallerIdentity caller, UUID requestedUserId) {
        if (caller.admin()) {
            return requestedUserId != null
                    ? bookingRepository.findByUserId(requestedUserId)
                    : bookingRepository.findAll();
        }
        if (requestedUserId != null && !requestedUserId.equals(caller.userId())) {
            throw new BookingAccessDeniedException("You can only list your own bookings.");
        }
        return bookingRepository.findByUserId(caller.userId());
    }

    @Transactional(readOnly = true)
    public Booking getBooking(UUID bookingId) {
        return requireBooking(bookingId);
    }

    @Transactional(readOnly = true)
    public Booking getBookingForCaller(UUID bookingId, CallerIdentity caller) {
        Booking booking = requireBooking(bookingId);
        if (!caller.admin() && !booking.getUserId().equals(caller.userId())) {
            throw new BookingAccessDeniedException(bookingId);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<Booking> listAll(Pageable pageable) {
        return bookingRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return bookingRepository.count();
    }

    @Transactional(readOnly = true)
    public List<BookingSeat> getBookingSeats(UUID bookingId) {
        return bookingSeatRepository.findByBookingId(bookingId);
    }

    /** Exposes the configured hold duration for DTOs / the controller. */
    public Duration getHoldDuration() {
        return holdDuration;
    }

    // ─── internal helpers ───────────────────────────────────────────────

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private List<ShowSeat> lockSeatsForBooking(UUID bookingId) {
        List<BookingSeat> lineItems = bookingSeatRepository.findByBookingId(bookingId);
        List<UUID> showSeatIds = lineItems.stream()
                .map(lineItem -> lineItem.getShowSeat().getId())
                .collect(Collectors.toList());
        return lockShowSeats(showSeatIds);
    }

    private List<ShowSeat> lockShowSeats(List<UUID> showSeatIds) {
        List<ShowSeat> seats = showSeatRepository.lockAllByIdIn(showSeatIds);
        if (seats.size() != showSeatIds.size()) {
            Set<UUID> found = seats.stream().map(ShowSeat::getId).collect(Collectors.toSet());
            UUID missing = showSeatIds.stream().filter(id -> !found.contains(id)).findFirst()
                    .orElseThrow();
            throw new EntityNotFoundException("ShowSeat not found: " + missing);
        }
        return seats;
    }

    /**
     * Lazy expiration: any locked seat whose hold has expired is reverted
     * to AVAILABLE inline, before the caller's own validation runs. This
     * ensures an expired hold never blocks a new hold or booking even
     * between scheduled cleanup runs.
     */
    private void expireStaleHolds(List<ShowSeat> seats) {
        Instant now = Instant.now();
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == ShowSeatStatus.HELD
                    && seat.getHoldExpiresAt() != null
                    && seat.getHoldExpiresAt().isBefore(now)) {
                clearHoldFields(seat);
            }
        }
    }

    private void clearHoldFields(ShowSeat seat) {
        seat.setStatus(ShowSeatStatus.AVAILABLE);
        seat.setHolderUserId(null);
        seat.setHeldAt(null);
        seat.setHoldExpiresAt(null);
    }

    private void validateSeatsInStatus(List<ShowSeat> seats, ShowSeatStatus expected) {
        for (ShowSeat seat : seats) {
            if (seat.getStatus() != expected) {
                throw new InvalidSeatStateException(seat.getId(), expected, seat.getStatus());
            }
        }
    }

    /**
     * Validates that every seat is HELD by the given user. Throws
     * {@link InvalidSeatStateException} if any seat is not HELD or is held
     * by a different user.
     */
    private void validateSeatsHeldByUser(List<ShowSeat> seats, UUID userId) {
        for (ShowSeat seat : seats) {
            if (seat.getStatus() != ShowSeatStatus.HELD) {
                throw new InvalidSeatStateException(seat.getId(), ShowSeatStatus.HELD, seat.getStatus());
            }
            if (!userId.equals(seat.getHolderUserId())) {
                throw new InvalidSeatStateException(seat.getId(), "is held by another user");
            }
        }
    }

    private void validateSeatsBelongToShow(List<ShowSeat> seats, UUID showId) {
        for (ShowSeat seat : seats) {
            if (!seat.getShowId().equals(showId)) {
                throw new SeatShowMismatchException(seat.getId(), showId, seat.getShowId());
            }
        }
    }

    private void requireNonEmpty(List<UUID> showSeatIds) {
        if (showSeatIds == null || showSeatIds.isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be specified.");
        }
        if (new HashSet<>(showSeatIds).size() != showSeatIds.size()) {
            throw new IllegalArgumentException("Duplicate seat ids in request.");
        }
    }
}
