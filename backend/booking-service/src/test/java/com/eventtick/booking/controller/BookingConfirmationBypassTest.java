package com.eventtick.booking.controller;

import com.eventtick.booking.TestTokens;
import com.eventtick.booking.config.SecurityConfig;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.security.JwtService;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatInventoryService;
import com.eventtick.booking.service.ShowSeatQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
 * Regression for the payment bypass found in Phase 15 Step 4: the public
 * {@code POST /api/bookings/{id}/confirm} let any signed-in customer confirm
 * an unpaid booking (seat BOOKED, zero payments). The endpoint is removed and
 * denied for every role — customer, other customer and admin alike (FR-39:
 * admin booking management is read-only) — with the one documented behavior:
 * <b>403</b> for a valid token, <b>401</b> for none/invalid. Confirmation
 * remains available only on {@code /internal/**}, for payment-service.
 *
 * <p>In every denial below, {@link BookingService#confirmBooking} must never
 * be reached: the booking and its seat stay untouched.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class})
class BookingConfirmationBypassTest {

    private static final String AUTH = "Authorization";
    private static final UUID OWNER = UUID.randomUUID();

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

    private static Booking unpaidBooking(UUID id) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUserId(OWNER);
        booking.setShowId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("500.00"));
        ReflectionTestUtils.setField(booking, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(booking, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return booking;
    }

    private void assertNeverConfirmed() {
        verify(bookingService, never()).confirmBooking(any());
    }

    // ---- 1-3: no role can confirm through the public path ----

    @Test
    void customer_cannotConfirmTheirOwnUnpaidBooking_403() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.confirmBooking(id)).thenReturn(unpaidBooking(id)); // would succeed if reached

        mockMvc.perform(post("/api/bookings/{id}/confirm", id).header(AUTH, TestTokens.customer(OWNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertNeverConfirmed();
    }

    @Test
    void customer_cannotConfirmAnotherCustomersUnpaidBooking_403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/bookings/{id}/confirm", id).header(AUTH, TestTokens.customer(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        assertNeverConfirmed();
    }

    @Test
    void admin_cannotConfirmABooking_403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/bookings/{id}/confirm", id).header(AUTH, TestTokens.admin(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        assertNeverConfirmed();
    }

    @Test
    void otherMethodsOnThePublicConfirmPath_areDeniedToo() throws Exception {
        UUID id = UUID.randomUUID();
        String admin = TestTokens.admin(UUID.randomUUID());

        mockMvc.perform(get("/api/bookings/{id}/confirm", id).header(AUTH, admin)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/bookings/{id}/confirm", id).header(AUTH, admin)).andExpect(status().isForbidden());

        assertNeverConfirmed();
    }

    // ---- 4-7: no / invalid credentials are 401, not 403 ----

    @Test
    void noToken_401() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        assertNeverConfirmed();
    }

    @Test
    void malformedToken_401() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/confirm", UUID.randomUUID()).header(AUTH, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        assertNeverConfirmed();
    }

    @Test
    void expiredToken_401() throws Exception {
        String expired = TestTokens.valid().subject(OWNER).expiresAt(Instant.now().minusSeconds(60)).build();

        mockMvc.perform(post("/api/bookings/{id}/confirm", UUID.randomUUID()).header(AUTH, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        assertNeverConfirmed();
    }

    @Test
    void tamperedToken_401() throws Exception {
        String token = TestTokens.valid().subject(OWNER).role("ADMIN").build();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(post("/api/bookings/{id}/confirm", UUID.randomUUID()).header(AUTH, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        assertNeverConfirmed();
    }

    // ---- 8: the internal confirmation, payment-service's path, still works ----

    @Test
    void internalConfirmation_stillWorks_andNeedsNoUserToken() throws Exception {
        UUID id = UUID.randomUUID();
        Booking confirmed = unpaidBooking(id);
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingService.confirmBooking(id)).thenReturn(confirmed);
        when(bookingService.getBookingSeats(id)).thenReturn(List.of());

        mockMvc.perform(post("/internal/bookings/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(bookingService).confirmBooking(id);
    }

    @Test
    void thePublicRoutesThatRemain_stillWork_forTheirOwner() throws Exception {
        UUID id = UUID.randomUUID();
        Booking booking = unpaidBooking(id);
        when(bookingService.getBookingForCaller(any(), any())).thenReturn(booking);
        when(bookingService.getBookingSeats(id)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{id}", id).header(AUTH, TestTokens.customer(OWNER)))
                .andExpect(status().isOk());
    }
}
