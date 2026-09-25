package com.eventtick.booking.controller;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.BookingStatus;
import com.eventtick.booking.service.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.7.1 ({@code GET /api/admin/bookings}) / FR-36
 * ({@code GET /api/admin/bookings/stats}). A {@code @WebMvcTest} slice —
 * real {@code GlobalExceptionHandler}, mocked {@link BookingService}; see
 * {@link BookingControllerRegressionTest} for why booking-service's H2 test
 * database can't be used for a real repository round-trip here. Nothing
 * about either endpoint's own logic needs one: {@code listBookings}
 * delegates to {@code BookingService.listAll} (Phase 13.7.1) and the
 * existing {@code BookingService.getBookingSeats}; {@code stats} delegates
 * to the new, one-line {@code BookingService.countAll}.
 *
 * <p>Deliberately does not test {@code /api/admin/** -> ROLE_ADMIN}
 * authorization — that is enforced at the gateway, not here (see the
 * gateway's own {@code GatewayAdminAuthorizationTest}).
 */
@WebMvcTest(AdminBookingController.class)
class AdminBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private static Booking booking() {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
        booking.setUserId(UUID.randomUUID());
        booking.setShowId(UUID.randomUUID());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(new BigDecimal("50.00"));
        ReflectionTestUtils.setField(booking, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(booking, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return booking;
    }

    // ---- returns 200 with paginated bookings ----

    @Test
    void listBookings_returns200_withPaginatedBookingsAcrossUsers() throws Exception {
        Booking first = booking();
        Booking second = booking();
        when(bookingService.listAll(any())).thenReturn(new PageImpl<>(List.of(first, second),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 2));
        when(bookingService.getBookingSeats(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bookingId").value(first.getId().toString()))
                .andExpect(jsonPath("$.content[0].userId").value(first.getUserId().toString()))
                .andExpect(jsonPath("$.content[1].bookingId").value(second.getId().toString()));
    }

    // ---- default pagination works ----

    @Test
    void listBookings_defaultPagination_isPageZeroSize20SortedByCreatedAtDesc() throws Exception {
        // The Page's own embedded Pageable is what gets serialized into the
        // response's number/size/sort fields — construct it to match the
        // real default so this test is actually verifying that default,
        // not just an arbitrary stub.
        Pageable defaultPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(bookingService.listAll(any())).thenReturn(new PageImpl<>(List.of(), defaultPageable, 0));

        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.sort.sorted").value(true));

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).listAll(captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(0);
        assertThat(used.getPageSize()).isEqualTo(20);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ---- explicit page/size/sort works ----

    @Test
    void listBookings_explicitPageSizeSort_isForwardedToTheService() throws Exception {
        when(bookingService.listAll(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/bookings?page=2&size=5&sort=totalAmount,asc"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).listAll(captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(5);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "totalAmount"));
    }

    // ---- service failure is handled using existing exception behavior ----

    @Test
    void listBookings_serviceFailure_isHandledByTheExistingGlobalExceptionHandler() throws Exception {
        when(bookingService.listAll(any())).thenThrow(new IllegalArgumentException("boom"));

        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("boom"));
    }

    // ---- the response is exactly the existing BookingResponse shape, nothing more/less ----

    @Test
    void response_isExactlyTheBookingResponseShape_noExtraOrLeakedFields() throws Exception {
        when(bookingService.listAll(any())).thenReturn(new PageImpl<>(List.of(booking())));
        when(bookingService.getBookingSeats(any())).thenReturn(List.of());

        String body = mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode item = objectMapper.readTree(body).path("content").get(0);
        Set<String> fieldNames = new java.util.LinkedHashSet<>();
        for (Iterator<String> it = item.fieldNames(); it.hasNext(); ) {
            fieldNames.add(it.next());
        }
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "bookingId", "userId", "showId", "status", "totalAmount", "seats", "createdAt", "updatedAt");
    }

    // ==================== GET /api/admin/bookings/stats ====================

    @Test
    void stats_validRequest_returns200() throws Exception {
        when(bookingService.countAll()).thenReturn(17L);

        mockMvc.perform(get("/api/admin/bookings/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void stats_totalBookings_reflectsBookingServiceCount() throws Exception {
        when(bookingService.countAll()).thenReturn(17L);

        mockMvc.perform(get("/api/admin/bookings/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(17));
    }

    @Test
    void stats_emptyDatabase_returnsZero() throws Exception {
        when(bookingService.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/bookings/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(0));
    }

    @Test
    void stats_response_containsExactlyTheOneExpectedField() throws Exception {
        when(bookingService.countAll()).thenReturn(3L);

        String body = mockMvc.perform(get("/api/admin/bookings/stats"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        Set<String> fieldNames = new java.util.LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            fieldNames.add(it.next());
        }
        assertThat(fieldNames).containsExactly("totalBookings");
    }
}
