package com.eventtick.booking.controller;

import com.eventtick.booking.TestTokens;
import com.eventtick.booking.config.SecurityConfig;
import com.eventtick.booking.dto.HoldSeatsRequest;
import com.eventtick.booking.dto.ReleaseSeatsRequest;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 22: HTTP-level tests for hold ownership enforcement.
 * Proves the controller validates userId matches JWT and passes the
 * authenticated caller to the service.
 */
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtService.class})
class HoldOwnershipHttpTest {

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

    private static ShowSeat heldSeat(UUID showId, UUID holderId) {
        ShowSeat seat = new ShowSeat();
        ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
        seat.setShowId(showId);
        seat.setSeatId(UUID.randomUUID());
        seat.setStatus(ShowSeatStatus.HELD);
        seat.setPrice(new BigDecimal("500.00"));
        seat.setHolderUserId(holderId);
        seat.setHeldAt(Instant.now());
        seat.setHoldExpiresAt(Instant.now().plusSeconds(600));
        return seat;
    }

    @Test
    void holdSeats_userIdMatchesJwt_succeeds() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = heldSeat(showId, CALLER);
        ReflectionTestUtils.setField(seat, "id", seatId);
        HoldSeatsRequest request = new HoldSeatsRequest(CALLER, List.of(seatId));
        when(bookingService.holdSeats(showId, List.of(seatId), CALLER)).thenReturn(List.of(seat));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/hold", showId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty());
    }

    @Test
    void holdSeats_userIdMismatchJwt_isForbidden() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        HoldSeatsRequest request = new HoldSeatsRequest(someoneElse, List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/hold", showId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(bookingService, never()).holdSeats(any(), any(), any());
    }

    @Test
    void releaseSeats_usesAuthenticatedCaller() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ShowSeat seat = new ShowSeat();
        ReflectionTestUtils.setField(seat, "id", seatId);
        seat.setShowId(showId);
        seat.setSeatId(UUID.randomUUID());
        seat.setStatus(ShowSeatStatus.AVAILABLE);
        seat.setPrice(new BigDecimal("500.00"));
        ReleaseSeatsRequest request = new ReleaseSeatsRequest(List.of(seatId));
        when(bookingService.releaseHoldForCaller(eq(List.of(seatId)), eq(new CallerIdentity(CALLER, false))))
                .thenReturn(List.of(seat));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/release", showId)
                        .header(AUTH, TestTokens.customer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(bookingService).releaseHoldForCaller(eq(List.of(seatId)), eq(new CallerIdentity(CALLER, false)));
    }

    @Test
    void holdSeats_withNoToken_gets401() throws Exception {
        UUID showId = UUID.randomUUID();
        HoldSeatsRequest request = new HoldSeatsRequest(CALLER, List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/hold", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(bookingService, never()).holdSeats(any(), any(), any());
    }
}
