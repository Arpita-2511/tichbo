package com.eventtick.booking.controller;

import com.eventtick.booking.dto.CancelBookingRequest;
import com.eventtick.booking.dto.CreateBookingRequest;
import com.eventtick.booking.dto.HoldSeatsRequest;
import com.eventtick.booking.dto.ReleaseSeatsRequest;
import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.service.BookingService;
import com.eventtick.booking.service.ShowSeatQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.7.1: proves the seven pre-existing {@code /api/bookings/**}
 * routes on {@link BookingController} are unaffected by the new, separate
 * {@link AdminBookingController}. Mirrors catalog-service's
 * {@code ShowControllerRegressionTest} (Phase 13.6.2).
 *
 * <p>{@code @WebMvcTest(BookingController.class)} — real
 * {@code GlobalExceptionHandler}/Bean Validation, mocked
 * {@link BookingService}/{@link ShowSeatQueryService}; booking-service has
 * no schema-generation mechanism for its H2 test database
 * ({@code ddl-auto: none} even for tests), so a real repository round-trip
 * isn't available here, and this endpoint's own logic doesn't need one —
 * every route already delegates entirely to the (unmodified) service layer.
 */
@WebMvcTest(BookingController.class)
class BookingControllerRegressionTest {

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

        mockMvc.perform(get("/api/bookings/shows/{showId}/seats", showId))
                .andExpect(status().isOk());
    }

    @Test
    void holdSeats_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        HoldSeatsRequest request = new HoldSeatsRequest(UUID.randomUUID(), List.of(seatId));
        when(bookingService.holdSeats(eq(showId), any(), any())).thenReturn(List.of(showSeat(showId)));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/hold", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void releaseSeats_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        ReleaseSeatsRequest request = new ReleaseSeatsRequest(List.of(seatId));
        when(bookingService.releaseHold(any())).thenReturn(List.of(showSeat(showId)));

        mockMvc.perform(post("/api/bookings/shows/{showId}/seats/release", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getBooking_stillWorks() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, UUID.randomUUID(), UUID.randomUUID());
        when(bookingService.getBooking(bookingId)).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk());
    }

    @Test
    void createBooking_stillWorks() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        CreateBookingRequest request = new CreateBookingRequest(UUID.randomUUID(), showId, List.of(seatId));
        Booking booking = booking(UUID.randomUUID(), request.userId(), showId);
        when(bookingService.createBooking(request.userId(), request.showId(), request.showSeatIds()))
                .thenReturn(booking);
        when(bookingService.getBookingSeats(booking.getId())).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmBooking_stillWorks() throws Exception {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, UUID.randomUUID(), UUID.randomUUID());
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingService.confirmBooking(bookingId)).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings/{bookingId}/confirm", bookingId))
                .andExpect(status().isOk());
    }

    @Test
    void cancelBooking_stillWorks() throws Exception {
        UUID bookingId = UUID.randomUUID();
        CancelBookingRequest request = new CancelBookingRequest(UUID.randomUUID());
        Booking booking = booking(bookingId, request.requestingUserId(), UUID.randomUUID());
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingService.cancelBooking(bookingId, request.requestingUserId())).thenReturn(booking);
        when(bookingService.getBookingSeats(bookingId)).thenReturn(List.of());

        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getBookingsForUser_stillWorks() throws Exception {
        UUID userId = UUID.randomUUID();
        Booking booking = booking(UUID.randomUUID(), userId, UUID.randomUUID());
        when(bookingService.getBookingsForUser(userId)).thenReturn(List.of(booking));
        when(bookingService.getBookingSeats(booking.getId())).thenReturn(List.of());

        mockMvc.perform(get("/api/bookings").param("userId", userId.toString()))
                .andExpect(status().isOk());
    }
}
