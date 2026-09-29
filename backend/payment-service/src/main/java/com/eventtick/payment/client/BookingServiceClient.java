package com.eventtick.payment.client;

import com.eventtick.payment.exception.BookingNotFoundException;
import com.eventtick.payment.exception.BookingServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * The one place payment-service talks to booking-service — direct
 * service-to-service HTTP, not through the Gateway (see
 * {@code application.yml}'s {@code booking-service.base-url} comment for
 * why: booking-service has no security of its own, the same way
 * catalog-service doesn't, so no additional credential is needed, and
 * there is no reason to route internal traffic back out through the
 * customer-facing edge). Deliberately the *only* class in this service
 * that knows booking-service's URL shape.
 *
 * <p>Uses booking-service's service-to-service surface —
 * {@code GET /internal/bookings/{id}}, {@code POST /internal/bookings/{id}/confirm},
 * {@code POST /internal/bookings/{id}/cancel} — see
 * {@code backend/booking-service/.../controller/InternalBookingController.java}.
 * Not the public {@code /api/bookings/**} endpoints: those enforce booking
 * ownership from the caller's JWT, and this service acts for the system
 * (its reconciliation sweep has no user token). The Gateway has no route
 * for {@code /internal/**}.
 */
@Component
public class BookingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceClient.class);

    private final RestClient restClient;

    public BookingServiceClient(@Value("${booking-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Authoritative booking data: owner, status, charge amount. Never trust a client-supplied equivalent. */
    public BookingSummary getBooking(UUID bookingId) {
        try {
            BookingApiResponse response = restClient.get()
                    .uri("/internal/bookings/{id}", bookingId)
                    .retrieve()
                    .body(BookingApiResponse.class);
            if (response == null) {
                throw new BookingServiceUnavailableException("booking-service returned an empty body for " + bookingId, null);
            }
            return new BookingSummary(response.bookingId(), response.userId(), response.status(), response.totalAmount());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new BookingNotFoundException(bookingId);
            }
            throw unavailable(bookingId, ex);
        } catch (RestClientException ex) {
            throw unavailable(bookingId, ex);
        }
    }

    /**
     * Best-effort: called after a payment has already been durably
     * recorded as {@code SUCCESS} (either from the original creation flow
     * or a later reconciliation retry — see {@code PaymentService}). A
     * failure here does not undo that status — see docs/architecture.md
     * §25.1 scenario G — the caller records the outcome via
     * {@code Payment.bookingSyncStatus} (§25.3) rather than this method
     * throwing back to the caller as a payment failure. Safe to call
     * repeatedly: booking-service's {@code confirmBooking} is idempotent
     * on an already-{@code CONFIRMED} booking (Phase 15 Step 3's one
     * booking-service change).
     *
     * @return {@code true} if booking-service acknowledged the call (2xx), {@code false} otherwise
     */
    public boolean confirmBooking(UUID bookingId) {
        try {
            restClient.post().uri("/internal/bookings/{id}/confirm", bookingId).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.warn("booking confirm failed for booking={} — needs reconciliation (see docs/architecture.md §25.3): {}",
                    bookingId, ex.toString());
            return false;
        }
    }

    /**
     * Best-effort, mirroring {@link #confirmBooking}: called after a
     * payment has already been durably recorded as {@code FAILED} or
     * {@code EXPIRED}. Reuses booking-service's existing {@code cancel}
     * endpoint as the Step 1 design's documented interim option
     * (booking-service has no dedicated "fail" transition yet — see
     * §25.1's open design question). Safe to call repeatedly: idempotent
     * on an already-{@code CANCELLED} booking (Phase 15 Step 3).
     *
     * @return {@code true} if booking-service acknowledged the call (2xx), {@code false} otherwise
     */
    public boolean releaseBooking(UUID bookingId) {
        try {
            restClient.post().uri("/internal/bookings/{id}/cancel", bookingId)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.warn("releasing booking={} failed — needs reconciliation: {}", bookingId, ex.toString());
            return false;
        }
    }

    private BookingServiceUnavailableException unavailable(UUID bookingId, Exception cause) {
        return new BookingServiceUnavailableException("booking-service call failed for booking " + bookingId, cause);
    }

    /** Only the fields this service actually reads from booking-service's BookingResponse. */
    private record BookingApiResponse(UUID bookingId, UUID userId, String status, java.math.BigDecimal totalAmount) {
    }
}
