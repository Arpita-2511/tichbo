package com.eventtick.booking.controller;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.service.ShowSeatQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.7.2: {@code GET /api/admin/shows/{showId}/seat-activity}. A
 * {@code @WebMvcTest} slice — real {@code GlobalExceptionHandler}, mocked
 * {@link ShowSeatQueryService}; see {@code BookingControllerRegressionTest}
 * for why booking-service's H2 test database can't be used for a real
 * repository round-trip here. Nothing about this endpoint's own logic
 * needs one: it delegates entirely to the existing, unmodified
 * {@code ShowSeatQueryService.getSeatMap}, the same query the
 * customer-facing {@code GET /api/bookings/shows/{showId}/seats} already
 * uses (that route's own behavior is covered by
 * {@code BookingControllerRegressionTest.getSeatMap_stillWorks}, not
 * duplicated here).
 *
 * <p>Deliberately does not test {@code /api/admin/** -> ROLE_ADMIN}
 * authorization — that is enforced at the gateway, not here.
 */
@WebMvcTest(AdminShowSeatActivityController.class)
class AdminShowSeatActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShowSeatQueryService showSeatQueryService;

    private static ShowSeat showSeat(ShowSeatStatus status) {
        try {
            Constructor<ShowSeat> ctor = ShowSeat.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ShowSeat seat = ctor.newInstance();
            ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
            seat.setShowId(UUID.randomUUID());
            seat.setSeatId(UUID.randomUUID());
            seat.setStatus(status);
            seat.setPrice(new BigDecimal("30.00"));
            return seat;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- valid UUID returns 200, using the existing seat-map response shape ----

    @Test
    void validShowId_returns200_withTheExistingSeatMapResponseShape() throws Exception {
        UUID showId = UUID.randomUUID();
        ShowSeat available = showSeat(ShowSeatStatus.AVAILABLE);
        ShowSeat held = showSeat(ShowSeatStatus.HELD);
        ShowSeat booked = showSeat(ShowSeatStatus.BOOKED);
        when(showSeatQueryService.getSeatMap(showId)).thenReturn(List.of(available, held, booked));

        mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(showId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(3))
                .andExpect(jsonPath("$.seats[0].showSeatId").value(available.getId().toString()))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].status").value("HELD"))
                .andExpect(jsonPath("$.seats[2].status").value("BOOKED"));
    }

    // ---- the showId path variable is forwarded correctly ----

    @Test
    void showId_isForwardedExactlyAsGiven_toShowSeatQueryService() throws Exception {
        UUID showId = UUID.randomUUID();
        when(showSeatQueryService.getSeatMap(showId)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", showId))
                .andExpect(status().isOk());

        verify(showSeatQueryService).getSeatMap(showId);
    }

    // ---- an empty show (no show_seats rows) is 200 with an empty list, not 404 ----

    @Test
    void showWithNoSeats_returns200_withAnEmptyList_notA404() throws Exception {
        UUID showId = UUID.randomUUID();
        when(showSeatQueryService.getSeatMap(showId)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", showId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(showId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(0));
    }

    // ---- invalid UUID follows the existing 400 handling ----

    @Test
    void malformedShowId_returns400_withTheExistingValidationErrorFormat() throws Exception {
        mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ---- a service/query failure is handled by the existing exception handling ----

    @Test
    void serviceFailure_isHandledByTheExistingGlobalExceptionHandler() throws Exception {
        UUID showId = UUID.randomUUID();
        when(showSeatQueryService.getSeatMap(showId)).thenThrow(new IllegalArgumentException("boom"));

        mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", showId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("boom"));
    }

    // ---- the response is exactly the existing SeatMapResponse/SeatMapItemDto shape ----

    @Test
    void response_isExactlyTheSeatMapResponseShape_noExtraOrLeakedFields() throws Exception {
        UUID showId = UUID.randomUUID();
        when(showSeatQueryService.getSeatMap(showId)).thenReturn(List.of(showSeat(ShowSeatStatus.AVAILABLE)));

        String body = mockMvc.perform(get("/api/admin/shows/{showId}/seat-activity", showId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("showId", "seats");
        assertThat(fieldNames(root.path("seats").get(0)))
                .containsExactlyInAnyOrder("showSeatId", "seatId", "status", "price");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
