package com.eventtick.booking.controller;

import com.eventtick.booking.TestTokens;
import com.eventtick.booking.config.SecurityConfig;
import com.eventtick.booking.dto.CreateBookingRequest;
import com.eventtick.booking.dto.HoldSeatsRequest;
import com.eventtick.booking.dto.ReleaseSeatsRequest;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.security.CallerIdentity;
import com.eventtick.booking.security.JwtService;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.7.1: proves the pre-existing {@code /api/bookings/**} routes on
 * {@link BookingController} are unaffected by the separate
 * {@link AdminBookingController}. Mirrors catalog-service's
 * {@code ShowControllerRegressionTest} (Phase 13.6.2).
 *
 * <p>Phase 15 Step 4 follow-up: booking ownership (BR-07). These routes now
 * run behind the real {@link SecurityConfig} with real bearer tokens, so this
 * class also proves the controller takes the caller's identity from the JWT
 * — never from a body field or query parameter — and passes it to the
 * service, whose own ownership rules are covered by
 * {@code BookingServiceOwnershipTest}.
 *
 * <p>{@code @WebMvcTest(BookingController.class)} — real
 * {@code GlobalExceptionHandler}/Bean Validation, mocked
 * {@link BookingService}/{@link ShowSeatQueryService}; booking-service has
 * no schema-generation mechanism for its H2 test database
 * ({@code ddl-auto: none} even for tests), so a real repository round-trip
 * isn't available here.
 */
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtService.class})
class BookingControllerRegressionTest {

    private static final UUID CALLER = UUID.randomUUID();
    private static final String AUTH = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private ShowSeatQueryService showSeatQueryService;

    private static ShowSeat showSeat(UUID showId) {
        try {
            Constructor<ShowSeat> ctor = ShowSeat.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ShowSeat seat = ctor.newInstance();
            ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
            seat.setShowId(showId);
            seat.setSeatId(UUID.randomUUID());
            seat.setStatus(ShowSeatStatus.AVAILABLE);
            seat.setPrice(new BigDecimal("25.00"));
            return seat;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Booking booking(UUID id, UUID userId, UUID showId) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUserId(userId);
        booking.setShowId(showId);
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("25.00"));
        ReflectionTestUtils.setField(booking, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(booking, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return booking;
    }

    @Test
    void getSeatMap_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        when(showSeatQueryService.getSeatMap(showId)).thenReturn(List.of(showSeat(showId)));

        mockMvc.perform(get("/api/bookings/shows/{showId}/seats", showId).header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isOk());
    }

    @Test
    void holdSeats_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        ShowSeat seat = showSeat(showId);
        seat.setStatus(ShowSeatStatus.HELD);
        HoldSeatsRequest request = new HoldSeatsRequest(CALLER, List.of(seat.getId()));
        when(bookingService.holdSeats(showId, request.showSeatIds(), CALLER)).thenReturn(List.of(seat));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/hold", showId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void releaseSeats_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        ShowSeat seat = showSeat(showId);
        ReleaseSeatsRequest request = new ReleaseSeatsRequest(List.of(seat.getId()));
        when(bookingService.releaseHold(request.showSeatIds())).thenReturn(List.of(seat));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/release", showId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getBooking_passesTheCallersIdentityFromTheJwt_toTheService() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, CALLER, UUID.randomUUID());
        when(bookingService.getBookingForCaller(bookingId, new CallerIdentity(CALLER, false))).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()));
    }

    @Test
    void getBooking_administratorIdentityIsPassedAsAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBookingForCaller(bookingId, new CallerIdentity(adminId, true)))
                .thenReturn(booking(bookingId, CALLER, UUID.randomUUID()));
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId).header(AUTH, TestTokens.admin(adminId)))
                .andExpect(status().isOk());
    }

    @Test
    void createBooking_stillWorks_whenBodyUserIdMatchesTheJwtSubject() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CreateBookingRequest request = new CreateBookingRequest(CALLER, showId, List.of(seatId));
        Booking booking = booking(UUID.randomUUID(), CALLER, showId);
        when(bookingService.createBooking(eq(CALLER), eq(showId), eq(request.showSeatIds()), any())).thenReturn(booking);
        when(bookingService.getBookingSeats(booking.getId())).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings")
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createBooking_propagatesTheIncomingXRequestId_asTheEventsCorrelationId() throws Exception {
        // Phase 16 Step 2: the Gateway already sets X-Request-ID on every
        // routed request; this proves the controller actually reads it and
        // forwards it, rather than the outbox event getting a fresh,
        // unrelated id every time.
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CreateBookingRequest request = new CreateBookingRequest(CALLER, showId, List.of(seatId));
        Booking booking = booking(UUID.randomUUID(), CALLER, showId);
        when(bookingService.createBooking(CALLER, showId, request.showSeatIds(), "gw-correlation-123")).thenReturn(booking);
        when(bookingService.getBookingSeats(booking.getId())).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings")
                        .header(AUTH, TestTokens.customer(CALLER))
                        .header("X-Request-ID", "gw-correlation-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(bookingService).createBooking(CALLER, showId, request.showSeatIds(), "gw-correlation-123");
    }

    @Test
    void createBooking_forSomeoneElsesUserId_isForbidden_andNothingIsCreated() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/bookings")
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(bookingService, never()).createBooking(any(), any(), any(), any());
    }

    @Test
    void publicConfirm_noLongerExists_itIsDeniedAndNeverReachesTheService() throws Exception {
        // Was confirmBooking_stillWorks: the public confirm endpoint let any
        // signed-in customer confirm an unpaid booking (payment bypass) and
        // was removed. Full coverage is in BookingConfirmationBypassTest.
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/api/bookings/{bookingId}/confirm", bookingId).header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isForbidden());

        verify(bookingService, never()).confirmBooking(any());
    }

    @Test
    void cancelBooking_usesTheJwtIdentity_andIgnoresAnyBody() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, CALLER, UUID.randomUUID());
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingService.cancelBookingForCaller(bookingId, new CallerIdentity(CALLER, false))).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        // A legacy client still sending {"requestingUserId": <someone else>} must not change who is acting.
        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", bookingId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestingUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        verify(bookingService).cancelBookingForCaller(eq(bookingId), eq(new CallerIdentity(CALLER, false)));
    }

    @Test
    void cancelBooking_worksWithNoBodyAtAll() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, CALLER, UUID.randomUUID());
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingService.cancelBookingForCaller(bookingId, new CallerIdentity(CALLER, false))).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", bookingId).header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isOk());
    }

    @Test
    void listBookings_withNoUserIdParam_asksTheServiceForTheCallersOwn() throws Exception {
        Booking booking = booking(UUID.randomUUID(), CALLER, UUID.randomUUID());
        when(bookingService.getBookingsForCaller(new CallerIdentity(CALLER, false), null)).thenReturn(List.of(booking));
        when(bookingService.getBookingSeats(booking.getId())).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings").header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listBookings_userIdParamIsForwardedAsARequest_notAsIdentity() throws Exception {
        UUID other = UUID.randomUUID();
        when(bookingService.getBookingsForCaller(new CallerIdentity(CALLER, false), other)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings").param("userId", other.toString()).header(AUTH, TestTokens.customer(CALLER)))
                .andExpect(status().isOk());

        verify(bookingService).getBookingsForCaller(new CallerIdentity(CALLER, false), other);
    }
}
