package com.eventtick.payment.controller;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.exception.BookingNotFoundException;
import com.eventtick.payment.exception.BookingServiceUnavailableException;
import com.eventtick.payment.exception.IdempotencyConflictException;
import com.eventtick.payment.exception.PaymentConflictException;
import com.eventtick.payment.exception.PaymentNotFoundException;
import com.eventtick.payment.exception.PaymentOwnershipException;
import com.eventtick.payment.security.PaymentRole;
import com.eventtick.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full {@code @SpringBootTest} (mocked {@link PaymentService}), not a
 * {@code @WebMvcTest} slice — unlike catalog-service/booking-service's own
 * controller tests, this service has a real {@code SecurityConfig} (CSRF
 * disabled, stateless JWT auth), and {@code @WebMvcTest}'s own default test
 * security auto-configuration does <b>not</b> pick that up, leaving CSRF
 * protection on and every POST here rejected with 403 regardless of what
 * this test does. Using the full context instead means the real, actually-
 * deployed security config applies. Matches user-service's own established
 * precedent for testing its secured controllers the same way. Authentication
 * is injected directly via
 * {@code SecurityMockMvcRequestPostProcessors.authentication} rather than
 * driving the real JWT filter chain (that's {@code PaymentSecurityTest}'s
 * job) — this class only proves the controller's own request/response
 * mapping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();

    private static Authentication customer(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static Authentication admin(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static Payment payment(UUID id, UUID userId, PaymentStatus status) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", id);
        payment.setBookingId(BOOKING_ID);
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("1450.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("key-1");
        return payment;
    }

    // ==================== POST /api/payments ====================

    @Test
    void createPayment_new_returns201WithLocation() throws Exception {
        Payment created = payment(UUID.randomUUID(), CALLER, PaymentStatus.SUCCESS);
        when(paymentService.createPayment(eq(CALLER), eq(BOOKING_ID), anyString(), any()))
                .thenReturn(new PaymentService.PaymentCreationResult(created, true));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/payments/" + created.getId()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(1450.00));
    }

    @Test
    void createPayment_replay_returns200_notCreated() throws Exception {
        Payment existing = payment(UUID.randomUUID(), CALLER, PaymentStatus.SUCCESS);
        when(paymentService.createPayment(eq(CALLER), eq(BOOKING_ID), anyString(), any()))
                .thenReturn(new PaymentService.PaymentCreationResult(existing, false));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createPayment_missingBookingId_is400() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_blankIdempotencyKey_is400() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_bookingNotFound_is404() throws Exception {
        when(paymentService.createPayment(any(), any(), anyString(), any())).thenThrow(new BookingNotFoundException(BOOKING_ID));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void createPayment_notTheOwner_is403() throws Exception {
        when(paymentService.createPayment(any(), any(), anyString(), any())).thenThrow(new PaymentOwnershipException(BOOKING_ID));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void createPayment_idempotencyConflict_is409() throws Exception {
        when(paymentService.createPayment(any(), any(), anyString(), any())).thenThrow(new IdempotencyConflictException("key-1"));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void createPayment_duplicateLivePayment_is409() throws Exception {
        when(paymentService.createPayment(any(), any(), anyString(), any())).thenThrow(PaymentConflictException.duplicateLivePayment(BOOKING_ID));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_PAYMENT_FOR_BOOKING"));
    }

    @Test
    void createPayment_bookingServiceUnavailable_is503_withoutLeakingDetails() throws Exception {
        when(paymentService.createPayment(any(), any(), anyString(), any()))
                .thenThrow(new BookingServiceUnavailableException("connect timed out to 10.0.0.5:8083", new RuntimeException("boom")));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customer(CALLER)))
                        .contentType("application/json")
                        .content("{\"bookingId\":\"" + BOOKING_ID + "\",\"idempotencyKey\":\"key-1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("BOOKING_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("The booking service is temporarily unavailable. Please try again shortly."));
    }

    // ==================== GET /api/payments/{paymentId} ====================

    @Test
    void getPayment_owner_returns200() throws Exception {
        Payment payment = payment(UUID.randomUUID(), CALLER, PaymentStatus.SUCCESS);
        when(paymentService.getPayment(payment.getId())).thenReturn(payment);
        when(paymentService.canView(payment, CALLER, PaymentRole.CUSTOMER)).thenReturn(true);

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(authentication(customer(CALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()));
    }

    @Test
    void getPayment_admin_canViewAnyonesPayment() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Payment payment = payment(UUID.randomUUID(), ownerId, PaymentStatus.SUCCESS);
        UUID adminId = UUID.randomUUID();
        when(paymentService.getPayment(payment.getId())).thenReturn(payment);
        when(paymentService.canView(payment, adminId, PaymentRole.ADMIN)).thenReturn(true);

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(authentication(admin(adminId))))
                .andExpect(status().isOk());
    }

    @Test
    void getPayment_notOwner_is404_notLeakingExistence() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Payment payment = payment(UUID.randomUUID(), ownerId, PaymentStatus.SUCCESS);
        UUID otherCustomer = UUID.randomUUID();
        when(paymentService.getPayment(payment.getId())).thenReturn(payment);
        when(paymentService.canView(payment, otherCustomer, PaymentRole.CUSTOMER)).thenReturn(false);

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(authentication(customer(otherCustomer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void getPayment_doesNotExist_is404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(paymentService.getPayment(missing)).thenThrow(PaymentNotFoundException.byId(missing));

        mockMvc.perform(get("/api/payments/" + missing).with(authentication(customer(CALLER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPayment_invalidUuid_is400() throws Exception {
        mockMvc.perform(get("/api/payments/not-a-uuid").with(authentication(customer(CALLER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==================== GET /api/payments?bookingId= ====================

    @Test
    void getPaymentForBooking_owner_returns200() throws Exception {
        Payment payment = payment(UUID.randomUUID(), CALLER, PaymentStatus.SUCCESS);
        when(paymentService.getPaymentForBooking(BOOKING_ID)).thenReturn(payment);
        when(paymentService.canView(payment, CALLER, PaymentRole.CUSTOMER)).thenReturn(true);

        mockMvc.perform(get("/api/payments?bookingId=" + BOOKING_ID).with(authentication(customer(CALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(BOOKING_ID.toString()));
    }

    @Test
    void getPaymentForBooking_missingParam_is400() throws Exception {
        mockMvc.perform(get("/api/payments").with(authentication(customer(CALLER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void getPaymentForBooking_noneExists_is404() throws Exception {
        when(paymentService.getPaymentForBooking(BOOKING_ID)).thenThrow(PaymentNotFoundException.forBooking(BOOKING_ID));

        mockMvc.perform(get("/api/payments?bookingId=" + BOOKING_ID).with(authentication(customer(CALLER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void response_isExactlyThePaymentResponseShape_noExtraOrLeakedFields() throws Exception {
        Payment payment = payment(UUID.randomUUID(), CALLER, PaymentStatus.SUCCESS);
        when(paymentService.getPayment(payment.getId())).thenReturn(payment);
        when(paymentService.canView(payment, CALLER, PaymentRole.CUSTOMER)).thenReturn(true);

        mockMvc.perform(get("/api/payments/" + payment.getId()).with(authentication(customer(CALLER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.bookingId").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.currency").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.provider").exists());
    }
}
