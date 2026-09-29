package com.eventtick.payment.controller;

import com.eventtick.payment.dto.CreatePaymentRequest;
import com.eventtick.payment.dto.PaymentResponse;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.exception.PaymentNotFoundException;
import com.eventtick.payment.security.PaymentRole;
import com.eventtick.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * REST surface for the three endpoints approved in Phase 15 Step 1 (see
 * docs/api-contracts.md's Payment API section). Every method delegates to
 * {@link PaymentService} and maps the result to a DTO — no payment rules
 * live in this class, same discipline as {@code BookingController}.
 *
 * <p>The caller's own identity comes from {@code Authentication} (set by
 * {@code JwtAuthenticationFilter}), never a request field — see
 * {@link CreatePaymentRequest}'s Javadoc for why that's a deliberate
 * departure from booking-service's own {@code CreateBookingRequest}.
 *
 * <p>Exceptions thrown by the service layer are not handled here — see
 * {@code com.eventtick.payment.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 1. Create (or idempotently replay) a payment for a booking the
     * caller owns.
     *
     * <p>{@code X-Request-ID} (Phase 16 Step 4): the Gateway already sets
     * this on every routed request, same as booking-service's own {@code
     * createBooking}. Reused as the {@code PaymentSucceeded} outbox
     * event's {@code correlationId} if this call reaches SUCCESS — see
     * {@link PaymentService#createPayment}.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request,
                                                           Authentication authentication,
                                                           @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        UUID callerUserId = callerId(authentication);
        PaymentService.PaymentCreationResult result =
                paymentService.createPayment(callerUserId, request.bookingId(), request.idempotencyKey(), requestId);

        PaymentResponse response = PaymentResponse.from(result.payment());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/payments/" + result.payment().getId())).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /** 2. Fetch one payment by id — owner or admin only. */
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable UUID paymentId, Authentication authentication) {
        Payment payment = paymentService.getPayment(paymentId);
        requireCanView(payment, authentication, paymentId);
        return PaymentResponse.from(payment);
    }

    /**
     * 3. Fetch the (most relevant) payment for a booking — owner or admin
     * only. {@code bookingId} is a required query parameter (not a
     * {@code params=} mapping condition), so a request missing it
     * produces the usual {@code MissingServletRequestParameterException}
     * -> 400, handled by {@code GlobalExceptionHandler} like every other
     * service.
     */
    @GetMapping
    public PaymentResponse getPaymentForBooking(@RequestParam UUID bookingId, Authentication authentication) {
        Payment payment = paymentService.getPaymentForBooking(bookingId);
        requireCanView(payment, authentication, payment.getId());
        return PaymentResponse.from(payment);
    }

    private void requireCanView(Payment payment, Authentication authentication, UUID paymentIdForNotFound) {
        UUID callerUserId = callerId(authentication);
        PaymentRole callerRole = callerRole(authentication);
        // Not this payment's owner and not an admin: reported as 404, not
        // 403 — see docs/api-contracts.md's Payment API section for why
        // (avoids confirming the record's existence to an unauthorized
        // caller; a considered, revisitable choice from Step 1).
        if (!paymentService.canView(payment, callerUserId, callerRole)) {
            throw PaymentNotFoundException.byId(paymentIdForNotFound);
        }
    }

    private UUID callerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private PaymentRole callerRole(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_ADMIN")) {
                return PaymentRole.ADMIN;
            }
        }
        return PaymentRole.CUSTOMER;
    }
}
