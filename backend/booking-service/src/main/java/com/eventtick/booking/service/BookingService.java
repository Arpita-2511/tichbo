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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 *   <li><b>Seat-hold ownership</b> (above) is still not enforced: hold,
 *   release and the seat map are open to any authenticated caller.</li>
 * </ul>
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

    public BookingService(BookingRepository bookingRepository,
                           BookingSeatRepository bookingSeatRepository,
                           ShowSeatRepository showSeatRepository,
                           OutboxService outboxService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showSeatRepository = showSeatRepository;
        this.outboxService = outboxService;
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
     * <p>Phase 16 Step 2: also writes a {@code BookingCreated} outbox row
     * (see {@link OutboxService}) in this same transaction, so the booking
     * and the event describing it can never disagree — either both commit
     * or neither does. Publishing to Kafka itself happens later,
     * independently, and never affects this method's own success or
     * failure (docs/architecture.md §48).
     *
     * @param correlationId ties the resulting event back to the request
     *                       that created this booking (its own
     *                       X-Request-ID) — see {@link OutboxService#record}
     * @throws SeatShowMismatchException if a showSeatId doesn't belong to {@code showId}
     * @throws InvalidSeatStateException if any seat isn't currently HELD
     */
    @Transactional
    public Booking createBooking(UUID userId, UUID showId, List<UUID> showSeatIds, String correlationId) {
        requireNonEmpty(showSeatIds);
        List<ShowSeat> seats = lockShowSeats(showSeatIds);
        validateSeatsBelongToShow(seats, showId);
        validateSeatsInStatus(seats, ShowSeatStatus.HELD);

        // A seat stays HELD after a booking is created, so HELD alone doesn't
        // stop a second booking on it. The row locks taken above serialize
        // concurrent callers: the later one sees the earlier booking committed.
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
     * Called from payment-service after a successful payment
     * ({@code BookingServiceClient.confirmBooking} — Phase 15).
     *
     * <p><b>Idempotent on an already-{@code CONFIRMED} booking</b> (Phase 15
     * Step 3): returns the booking as-is rather than throwing, specifically
     * so payment-service's reconciliation sweep can safely retry this call
     * after a network failure without knowing whether the first attempt
     * actually landed — see {@code docs/architecture.md} §25.3. This is the
     * only behavior change; every other status still throws exactly as
     * before.
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
        seats.forEach(seat -> seat.setStatus(ShowSeatStatus.BOOKED));

        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }

    /**
     * Cancels a {@code PENDING} or {@code CONFIRMED} booking; its seats
     * are released back to {@code AVAILABLE} regardless of whether they
     * were {@code HELD} or {@code BOOKED}. Also called from payment-service
     * after a failed or expired payment
     * ({@code BookingServiceClient.releaseBooking} —
     * Phase 15).
     *
     * <p><b>Idempotent on an already-{@code CANCELLED} booking</b> (Phase 15
     * Step 3), for the same reconciliation-retry reason as
     * {@link #confirmBooking} above.
     *
     * <p><b>No ownership check</b> — this is the system-level cancel, used by
     * payment-service through {@code /internal/**}. A customer-initiated
     * cancel goes through {@link #cancelBookingForCaller}.
     *
     * @throws BookingNotFoundException if bookingId doesn't exist
     * @throws InvalidBookingStateException if the booking is FAILED, or already CANCELLED only via a different path than this method's own idempotent return (see above)
     */
    @Transactional
    public Booking cancelBooking(UUID bookingId) {
        return cancelExisting(requireBooking(bookingId));
    }

    /**
     * Customer-initiated cancel (BR-07): only the booking's owner may cancel
     * it. Administrators are not exempt — FR-39 defines admin booking
     * management as read-only. The ownership check runs after the existence
     * check (404 for a nonexistent booking, 403 for someone else's) and
     * before any state or seat change.
     *
     * @throws BookingNotFoundException      if bookingId doesn't exist
     * @throws BookingAccessDeniedException  if the caller does not own the booking
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
        seats.forEach(seat -> seat.setStatus(ShowSeatStatus.AVAILABLE));

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }

    /**
     * A user's bookings, most-recent-first ordering not yet applied. Plain
     * read with no authorization — internal use. Callers acting for a
     * signed-in user go through {@link #getBookingsForCaller}.
     */
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId);
    }

    /**
     * Bookings visible to {@code caller} (BR-07). A customer always gets
     * their own bookings; naming a different user is refused rather than
     * silently ignored. An administrator gets the named user's bookings, or
     * every booking when no user is named. Ownership comes from the validated
     * JWT, never from a request field.
     *
     * @param requestedUserId optional {@code userId} query parameter
     * @throws BookingAccessDeniedException if a customer asks for someone else's bookings
     */
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

    /**
     * A single booking by id. Plain read with no authorization — internal
     * use (payment-service). Callers acting for a signed-in user go through
     * {@link #getBookingForCaller}.
     *
     * @throws BookingNotFoundException if bookingId doesn't exist
     */
    @Transactional(readOnly = true)
    public Booking getBooking(UUID bookingId) {
        return requireBooking(bookingId);
    }

    /**
     * A booking for a signed-in caller (BR-07): its owner or an administrator.
     *
     * @throws BookingNotFoundException     if bookingId doesn't exist
     * @throws BookingAccessDeniedException if the caller is neither the owner nor an admin
     */
    @Transactional(readOnly = true)
    public Booking getBookingForCaller(UUID bookingId, CallerIdentity caller) {
        Booking booking = requireBooking(bookingId);
        if (!caller.admin() && !booking.getUserId().equals(caller.userId())) {
            throw new BookingAccessDeniedException(bookingId);
        }
        return booking;
    }

    /**
     * All bookings across every user, for {@code GET /api/admin/bookings}
     * (Phase 13.7.1). Unlike {@link #getBookingsForUser}, not scoped to one
     * user — admin-only access is enforced once, at the gateway
     * ({@code /api/admin/** -> ROLE_ADMIN}), the same boundary every other
     * admin-only path relies on; no check happens here. Plain
     * {@link BookingRepository#findAll(Pageable)}: {@link Booking} has no
     * lazy associations for a response mapper to read (unlike, say,
     * user-service's {@code User.plan}), so no entity graph is needed.
     */
    @Transactional(readOnly = true)
    public Page<Booking> listAll(Pageable pageable) {
        return bookingRepository.findAll(pageable);
    }

    /**
     * {@code GET /api/admin/bookings/stats} (Phase 13, FR-36). A live count
     * — no admin-only check here, same boundary as {@link #listAll}. FR-36
     * requires this figure to be sourced live from its owning service, not
     * cached or duplicated, so this reads {@link BookingRepository#count()}
     * directly on every call.
     */
    @Transactional(readOnly = true)
    public long countAll() {
        return bookingRepository.count();
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
