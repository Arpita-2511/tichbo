package com.eventtick.booking.controller;

import com.eventtick.booking.TestTokens;
import com.eventtick.booking.config.SecurityConfig;
import com.eventtick.booking.dto.ShowSeatDefinition;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.DuplicateShowSeatException;
import com.eventtick.booking.security.JwtService;
import com.eventtick.booking.service.ShowSeatInventoryService;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/admin/shows/{showId}/seats}. A {@code @WebMvcTest} slice
 * with the real {@link SecurityConfig}/{@link JwtService} and real
 * {@code GlobalExceptionHandler}/Bean Validation — mocked
 * {@link ShowSeatInventoryService}, the same split
 * {@code AdminShowSeatActivityControllerTest}/{@code BookingControllerRegressionTest}
 * already use: this layer proves authorization and HTTP-shape behavior,
 * not persistence (that's {@code ShowSeatInventoryServiceTest}, against a
 * real H2 repository).
 */
@WebMvcTest(AdminShowSeatInventoryController.class)
@Import({SecurityConfig.class, JwtService.class})
class AdminShowSeatInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShowSeatInventoryService showSeatInventoryService;

    private static ShowSeat showSeat(UUID showId, UUID seatId, BigDecimal price) {
        try {
            Constructor<ShowSeat> ctor = ShowSeat.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ShowSeat seat = ctor.newInstance();
            ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
            seat.setShowId(showId);
            seat.setSeatId(seatId);
            seat.setStatus(ShowSeatStatus.AVAILABLE);
            seat.setPrice(price);
            return seat;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private String requestJson(UUID seatId, String price) {
        return "{\"seats\":[{\"seatId\":\"" + seatId + "\",\"price\":" + price + "}]}";
    }

    // ---- ADMIN: successful creation ----

    @Test
    void admin_validRequest_returns201_withTheExistingSeatMapResponseShape() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(showSeatInventoryService.createSeats(eq(showId), any()))
                .thenReturn(List.of(showSeat(showId, seatId, new BigDecimal("500.00"))));

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "500.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/bookings/shows/" + showId + "/seats"))
                .andExpect(jsonPath("$.showId").value(showId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(1))
                .andExpect(jsonPath("$.seats[0].seatId").value(seatId.toString()))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[0].price").value(500.00));
    }

    @Test
    void admin_requestBody_isForwardedExactly_toTheService() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(showSeatInventoryService.createSeats(eq(showId), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "250.50")))
                .andExpect(status().isCreated());

        verify(showSeatInventoryService).createSeats(eq(showId),
                eq(List.of(new ShowSeatDefinition(seatId, new BigDecimal("250.50")))));
    }

    // ---- CUSTOMER: rejected ----

    @Test
    void customer_isRejected_with403_andTheServiceIsNeverCalled() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.customer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "500.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        verify(showSeatInventoryService, never()).createSeats(any(), any());
    }

    // ---- Unauthenticated: rejected ----

    @Test
    void unauthenticated_isRejected_with401_andTheServiceIsNeverCalled() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "500.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        verify(showSeatInventoryService, never()).createSeats(any(), any());
    }

    // ---- Invalid request validation ----

    @Test
    void admin_emptySeatsList_returns400_withTheExistingValidationErrorFormat() throws Exception {
        UUID showId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(showSeatInventoryService, never()).createSeats(any(), any());
    }

    @Test
    void admin_missingSeatId_returns400() throws Exception {
        UUID showId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":[{\"price\":100.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void admin_negativePrice_returns400() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "-10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void admin_malformedShowId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/shows/{showId}/seats", "not-a-uuid")
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(UUID.randomUUID(), "100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ---- Duplicate handling surfaced through the existing exception handler ----

    @Test
    void admin_duplicateSeatWithinRequest_returns400_fromTheServicesIllegalArgumentException() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(showSeatInventoryService.createSeats(eq(showId), any()))
                .thenThrow(new IllegalArgumentException("Duplicate seatId in request: " + seatId + "."));

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":[{\"seatId\":\"" + seatId + "\",\"price\":100.00},"
                                + "{\"seatId\":\"" + seatId + "\",\"price\":150.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Duplicate seatId in request: " + seatId + "."));
    }

    @Test
    void admin_existingInventory_returns409_withDuplicateShowSeatErrorCode() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(showSeatInventoryService.createSeats(eq(showId), any()))
                .thenThrow(new DuplicateShowSeatException(showId, List.of(seatId)));

        mockMvc.perform(post("/api/admin/shows/{showId}/seats", showId)
                        .header("Authorization", TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(seatId, "100.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("DUPLICATE_SHOW_SEAT"))
                .andExpect(jsonPath("$.message").value(containsString(seatId.toString())));
    }
}
