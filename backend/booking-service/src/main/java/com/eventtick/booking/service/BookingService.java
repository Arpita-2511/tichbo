package com.eventtick.booking.service;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingSeat;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.BookingNotFoundException;
import com.eventtick.booking.exception.InvalidBookingStateException;
import com.eventtick.booking.exception.InvalidSeatStateException;
import com.eventtick.booking.exception.SeatShowMismatchException;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.BookingSeatRepository;
import com.eventtick.booking.repository.ShowSeatRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seat-hold and booking lifecycle: {@code AVAILABLE → HELD → BOOKED} on
 * {@code show_seats}, and {@code PENDING → CONFIRMED / CANCELLED} on
 * {@code bookings}.
 *
 * <h2>Concurrency — what is and isn't handled</h2>
 * Every method that transitions a {@code ShowSeat}'s status
 * ({@link #holdSeats}, {@link #releaseHold}, the seat transitions inside
 * {@link #confirmBooking} and {@link #cancelBooking}) goes through
 * {@link #lockShowSeats}, which uses
 * {@link ShowSeatRepository#lockAllByIdIn} — a {@code SELECT ... FOR
 * UPDATE} row lock — inside an {@code @Transactional} method. This is
 * exactly what prevents two concurrent requests from both successfully
 * claiming the same seat: the second request's lock acquisition blocks
 * until the first transaction commits, so the second request re-reads the
 * post-commit status and correctly fails its status check instead of
 * racing on stale data. This part is safe today and requires no schema
 * change or Redis.
 *
 * <h2>What is NOT safe / NOT implemented yet</h2>
 * <ul>
 *   <li><b>Hold ownership.</b> {@code show_seats} has no column recording
 *   <i>who</i> holds a seat. {@link #holdSeats} accepts a {@code userId}
 *   parameter, but it is currently unused — not stored, not checked. Any
 *   caller who knows a {@code showSeatId} can currently release or act on
 *   a hold regardless of who created it. This is the specific gap Redis
 *   is expected to close (see {@code docs/architecture.md} §16):
 *   ownership + hold identity belongs in a Redis-backed hold record, not a
 *   new column bolted onto this table.</li>
 *   <li><b>Hold expiry.</b> There is no TTL anywhere. A seat set to
 *   {@code HELD} stays {@code HELD} forever unless something explicitly
 *   calls {@link #releaseHold} or {@link #cancelBooking}. Until Redis (or
 *   a scheduled sweep) exists, an abandoned checkout permanently locks a
 *   seat. Do not mistake the current behavior for "temporary."</li>
 *   <li><b>Authorization.</b> {@link #cancelBooking} accepts a
 *   {@code requestingUserId} but does not check it against
 *   {@code booking.getUserId()} — there is no authentication layer yet to
 *   trust that value. The parameter exists so the eventual check has
 *   somewhere to go.</li>
 * </ul>
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;

    public BookingService(BookingRepository bookingRepository,
                           BookingSeatRepository bookingSeatRepository,
                           ShowSeatRepository showSeatRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showSeatRepository = showSeatRepository;
    }

    /**
     * Transitions the given seats {@code AVAILABLE -> HELD} for one show.
     * All requested seats succeed together or none do.
     *
     * @param userId accepted but currently unused — see class Javadoc
     *               ("Hold ownership"). Not persisted anywhere.
     * @throws SeatShowMismatchException if a showSeatId doesn't belong to {@code showId}
     * @throws InvalidSeatStateException if any seat isn't currently AVAILABLE
     */
    @Transactional
    public List<ShowSeat> holdSeats(UUID showId, List<UUID> showSeatIds, UUID userId) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        validateSeatsBelongToShow(seats, showId);
        validateSeatsInStatus(seats, ShowSeatStatus.AVAILABLE);
        seats.forEach(seat -> seat.setStatus(ShowSeatStatus.HELD));
        return seats;
    }

    /**
     * Best-effort, idempotent release: any of the given seats currently
     * {@code HELD} are reverted to {@code AVAILABLE}; seats already
     * {@code AVAILABLE} or already {@code BOOKED} are left untouched
     * rather than treated as an error. This is deliberately lenient — see
     * class Javadoc ("Hold expiry") for why an explicit release is the
     * only way to free a held seat right now.
     */
    @Transactional
    public List<ShowSeat> releaseHold(List<UUID> showSeatIds) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == ShowSeatStatus.HELD) {
                seat.setStatus(ShowSeatStatus.AVAILABLE);
            }
        }
        return seats;
    }

    /**
     * Records a new {@code PENDING} booking for seats that must already be
     * {@code HELD} (by a prior {@link #holdSeats} call) — this method does
     * not itself claim {@code AVAILABLE} seats. {@code totalAmount} is
     * computed here as the sum of each seat's current price.
     *
     * @throws SeatShowMismatchException if a showSeatId doesn't belong to {@code showId}
     * @throws InvalidSeatStateException if any seat isn't currently HELD
     */
    @Transactional
    public Booking createBooking(UUID userId, UUID showId, List<UUID> showSeatIds) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        validateSeatsBelongToShow(seats, showId);
        validateSeatsInStatus(seats, ShowSeatStatus.HELD);

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

        return savedBooking;
    }

    /**
     * Confirms a {@code PENDING} booking: its seats transition
     * {@code HELD -> BOOKED} and the booking becomes {@code CONFIRMED}.
     * There is no payment step yet — this is currently the only trigger
     * for confirmation, called directly rather than from a payment-success
     * callback.
     *
     * @throws BookingNotFoundException if bookingId doesn't exist
     * @throws InvalidBookingStateException if the booking isn't PENDING
     * @throws InvalidSeatStateException if any of its seats aren't HELD
     */
    @Transactional
    public Booking confirmBooking(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), "confirmed");
        }

        List<ShowSeat> seats = lockSeatsForBooking(bookingId);
        validateSeatsInStatus(seats, ShowSeatStatus.HELD);
        seats.forEach(seat -> seat.setStatus(ShowSeatStatus.BOOKED));

        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }

    /**
     * Cancels a {@code PENDING} or {@code CONFIRMED} booking; its seats
     * are released back to {@code AVAILABLE} regardless of whether they
     * were {@code HELD} or {@code BOOKED}.
     *
     * @param requestingUserId accepted but not yet checked against the
     *                         booking's owner — see class Javadoc ("Authorization").
     * @throws BookingNotFoundException if bookingId doesn't exist
     * @throws InvalidBookingStateException if the booking is already CANCELLED or FAILED
     */
    @Transactional
    public Booking cancelBooking(UUID bookingId, UUID requestingUserId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(bookingId, booking.getStatus(), "cancelled");
        }

        List<ShowSeat> seats = lockSeatsForBooking(bookingId);
        seats.forEach(seat -> seat.setStatus(ShowSeatStatus.AVAILABLE));

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }

    /**
     * A user's bookings, most-recent-first ordering not yet applied. Plain
     * read; safe to call at any time. Does not itself enforce that the
     * caller is allowed to see {@code userId}'s bookings (BR-07) — that
     * depends on the future auth layer.
     */
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId);
    }

    /**
     * A single booking by id. Plain read; safe to call at any time.
     *
     * @throws BookingNotFoundException if bookingId doesn't exist
     */
    @Transactional(readOnly = true)
    public Booking getBooking(UUID bookingId) {
        return requireBooking(bookingId);
    }

    /**
     * Read-only accessor for a booking's seat line items. Added to let the
     * REST layer assemble {@code BookingResponse.seats} (per the approved
     * API contract) through the service, rather than a controller calling
     * {@link BookingSeatRepository} directly. Performs no locking and no
     * validation beyond what the repository itself does — this is not new
     * business logic, just a plain read already used internally by
     * {@link #lockSeatsForBooking}.
     */
    @Transactional(readOnly = true)
    public List<BookingSeat> getBookingSeats(UUID bookingId) {
        return bookingSeatRepository.findByBookingId(bookingId);
    }

    // ─── internal helpers ───────────────────────────────────────────────

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /** Locked fetch of the ShowSeats referenced by a booking's line items. */
    private List<ShowSeat> lockSeatsForBooking(UUID bookingId) {
        List<BookingSeat> lineItems = bookingSeatRepository.findByBookingId(bookingId);
        List<UUID> showSeatIds = lineItems.stream()
                .map(lineItem -> lineItem.getShowSeat().getId())
                .collect(Collectors.toList());
        return lockShowSeats(showSeatIds);
    }

    /**
     * Locks and fetches the given ShowSeats via
     * {@link ShowSeatRepository#lockAllByIdIn} — see class Javadoc for why
     * this is the concurrency-safe operation. Every caller of this method
     * must already be running inside an {@code @Transactional} method.
     *
     * @throws EntityNotFoundException if any id doesn't resolve to a real ShowSeat
     */
    private List<ShowSeat> lockShowSeats(List<UUID> showSeatIds) {
        List<ShowSeat> seats = showSeatRepository.lockAllByIdIn(showSeatIds);
        if (seats.size() != showSeatIds.size()) {
            Set<UUID> found = seats.stream().map(ShowSeat::getId).collect(Collectors.toSet());
            UUID missing = showSeatIds.stream().filter(id -> !found.contains(id)).findFirst()
                    .orElseThrow(); // sizes differ, so at least one is missing
            throw new EntityNotFoundException("ShowSeat not found: " + missing);
        }
        return seats;
    }

    private void validateSeatsInStatus(List<ShowSeat> seats, ShowSeatStatus expected) {
        for (ShowSeat seat : seats) {
            if (seat.getStatus() != expected) {
                throw new InvalidSeatStateException(seat.getId(), expected, seat.getStatus());
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
