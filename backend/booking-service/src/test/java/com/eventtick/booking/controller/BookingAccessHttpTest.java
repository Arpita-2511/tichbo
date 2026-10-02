package com.eventtick.booking.controller;

import com.eventtick.booking.TestTokens;
import com.eventtick.booking.config.SecurityConfig;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.exception.BookingAccessDeniedException;
import com.eventtick.booking.exception.BookingNotFoundException;
import com.eventtick.booking.security.CallerIdentity;
import com.eventtick.booking.security.JwtService;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatInventoryService;
import com.eventtick.booking.service.ShowSeatQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-07 over HTTP with the real {@link SecurityConfig}, real JWT filter and
 * real {@code GlobalExceptionHandler}: which status a caller gets for a
 * booking they own, one they don't, one that doesn't exist, and no/invalid
 * credentials — plus the admin-route and {@code /internal/**} boundaries.
 * The ownership <i>rules</i> themselves are covered by
 * {@code BookingServiceOwnershipTest}; here the service is mocked to throw
 * what those rules throw, proving the exception-to-status mapping.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class})
class BookingAccessHttpTest {

    private static final String AUTH = "Authorization";
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private ShowSeatQueryService showSeatQueryService;

    // Bare @WebMvcTest (no controller class argument) loads every controller
    // in the service, including AdminShowSeatInventoryController — its
    // dependency must be mocked here too, same as the two services above,
    // or the context fails to start.
    @MockBean
    private ShowSeatInventoryService showSeatInventoryService;

    private static Booking booking(UUID id, UUID userId) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUserId(userId);
        booking.setShowId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("25.00"));
        ReflectionTestUtils.setField(booking, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(booking, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return booking;
    }

    // ---- GET /api/bookings/{id} ----

    @Test
    void getBooking_owner_gets200() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingForCaller(id, new CallerIdentity(CUSTOMER, false))).thenReturn(booking(id, CUSTOMER));
        when(bookingService.getBookingSeats(id)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{id}", id).header(AUTH, TestTokens.customer(CUSTOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void getBooking_otherCustomersBooking_gets403() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingForCaller(any(), any())).thenThrow(new BookingAccessDeniedException(id));

        mockMvc.perform(get("/api/bookings/{id}", id).header(AUTH, TestTokens.customer(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void getBooking_nonexistent_gets404_notForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingForCaller(any(), any())).thenThrow(new BookingNotFoundException(id));

        mockMvc.perform(get("/api/bookings/{id}", id).header(AUTH, TestTokens.customer(CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void getBooking_admin_gets200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingForCaller(id, new CallerIdentity(adminId, true))).thenReturn(booking(id, CUSTOMER));
        when(bookingService.getBookingSeats(id)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{id}", id).header(AUTH, TestTokens.admin(adminId)))
                .andExpect(status().isOk());
    }

    // ---- GET /api/bookings (list) ----

    @Test
    void listBookings_customerNamingSomeoneElse_gets403() throws Exception {
        when(bookingService.getBookingsForCaller(any(), any()))
                .thenThrow(new BookingAccessDeniedException("You can only list your own bookings."));

        mockMvc.perform(get("/api/bookings").param("userId", UUID.randomUUID().toString())
                        .header(AUTH, TestTokens.customer(CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    // ---- cancel ----

    @Test
    void cancel_otherCustomersBooking_gets403() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.cancelBookingForCaller(any(), any())).thenThrow(new BookingAccessDeniedException(id));

        mockMvc.perform(post("/api/bookings/{id}/cancel", id).header(AUTH, TestTokens.customer(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void cancel_nonexistent_gets404() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.cancelBookingForCaller(any(), any())).thenThrow(new BookingNotFoundException(id));

        mockMvc.perform(post("/api/bookings/{id}/cancel", id).header(AUTH, TestTokens.customer(CUSTOMER)))
                .andExpect(status().isNotFound());
    }

    // ---- unauthenticated / invalid credentials: 401, service never reached ----

    @Test
    void protectedRoutes_withNoToken_get401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/bookings/{id}", id)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/{id}/cancel", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/{id}/confirm", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/shows/{id}/seats", id)).andExpect(status().isUnauthorized());

        verify(bookingService, never()).getBookingForCaller(any(), any());
        verify(bookingService, never()).cancelBookingForCaller(any(), any());
    }

    @Test
    void malformedToken_gets401() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID()).header(AUTH, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_gets401() throws Exception {
        String expired = TestTokens.valid().subject(CUSTOMER).expiresAt(Instant.now().minusSeconds(60)).build();

        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID()).header(AUTH, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedSignature_gets401() throws Exception {
        String token = TestTokens.valid().subject(CUSTOMER).build();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID()).header(AUTH, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithAnUnknownRole_gets401() throws Exception {
        String token = TestTokens.valid().subject(CUSTOMER).role("SUPERUSER").build();

        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID()).header(AUTH, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBodyOrQueryUserId_neverSubstitutesForAToken() throws Exception {
        mockMvc.perform(get("/api/bookings").param("userId", CUSTOMER.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ---- admin routes (defense in depth behind the Gateway) ----

    @Test
    void adminRoute_asACustomer_gets403() throws Exception {
        mockMvc.perform(get("/api/admin/bookings").header(AUTH, TestTokens.customer(CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoute_asAnAdmin_gets200() throws Exception {
        when(bookingService.listAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/bookings").header(AUTH, TestTokens.admin(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoute_withNoToken_gets401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
    }

    // ---- /internal/**: payment-service's system-level surface ----

    @Test
    void internal_getConfirmCancel_needNoToken_andCarryNoOwnershipCheck() throws Exception {
        UUID id = UUID.randomUUID();
        Booking booking = booking(id, CUSTOMER);
        when(bookingService.getBooking(id)).thenReturn(booking);
        when(bookingService.confirmBooking(id)).thenReturn(booking);
        when(bookingService.cancelBooking(id)).thenReturn(booking);
        when(bookingService.getBookingSeats(id)).thenReturn(List.of());

        mockMvc.perform(get("/internal/bookings/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(post("/internal/bookings/{id}/confirm", id)).andExpect(status().isOk());
        mockMvc.perform(post("/internal/bookings/{id}/cancel", id)).andExpect(status().isOk());
    }

    @Test
    void internal_nonexistentBooking_gets404() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBooking(id)).thenThrow(new BookingNotFoundException(id));

        mockMvc.perform(get("/internal/bookings/{id}", id)).andExpect(status().isNotFound());
    }
}
