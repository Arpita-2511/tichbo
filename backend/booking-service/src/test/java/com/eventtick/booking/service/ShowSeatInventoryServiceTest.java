package com.eventtick.booking.service;

import com.eventtick.booking.dto.ShowSeatDefinition;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.exception.DuplicateShowSeatException;
import com.eventtick.booking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-repository round-trip for {@link ShowSeatInventoryService}, the same
 * {@code create-drop} H2 pattern as {@code BookingCreationOutboxIntegrationTest}
 * (booking-service's H2 test database otherwise runs {@code ddl-auto: none}).
 * Needed here specifically because {@link #createSeats_existingInventory_isRejected}
 * must prove rejection against a row that is actually already persisted —
 * a pure-mock test could only prove the code calls the right repository
 * method, not that the check is correct against real data.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ShowSeatInventoryServiceTest {

    @Autowired
    private ShowSeatInventoryService showSeatInventoryService;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Test
    void createSeats_singleSeat_isCreated_available_withTheGivenPrice() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        List<ShowSeat> created = showSeatInventoryService.createSeats(
                showId, List.of(new ShowSeatDefinition(seatId, new BigDecimal("450.00"))));

        assertThat(created).hasSize(1);
        ShowSeat seat = created.get(0);
        assertThat(seat.getId()).isNotNull();
        assertThat(seat.getShowId()).isEqualTo(showId);
        assertThat(seat.getSeatId()).isEqualTo(seatId);
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(seat.getPrice()).isEqualByComparingTo("450.00");
        assertThat(showSeatRepository.findById(seat.getId())).isPresent();
    }

    @Test
    void createSeats_multipleSeats_forOneShow_areAllCreated_andAllAvailable() {
        UUID showId = UUID.randomUUID();
        List<ShowSeatDefinition> definitions = List.of(
                new ShowSeatDefinition(UUID.randomUUID(), new BigDecimal("100.00")),
                new ShowSeatDefinition(UUID.randomUUID(), new BigDecimal("200.00")),
                new ShowSeatDefinition(UUID.randomUUID(), new BigDecimal("300.00")));

        List<ShowSeat> created = showSeatInventoryService.createSeats(showId, definitions);

        assertThat(created).hasSize(3);
        assertThat(created).allSatisfy(seat -> {
            assertThat(seat.getShowId()).isEqualTo(showId);
            assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        });
        assertThat(showSeatRepository.findByShowId(showId)).hasSize(3);
    }

    @Test
    void createSeats_duplicateSeatIdWithinOneRequest_isRejected_andNothingIsSaved() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        long before = showSeatRepository.count();

        assertThatThrownBy(() -> showSeatInventoryService.createSeats(showId, List.of(
                new ShowSeatDefinition(seatId, new BigDecimal("100.00")),
                new ShowSeatDefinition(seatId, new BigDecimal("150.00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(seatId.toString());

        assertThat(showSeatRepository.count()).isEqualTo(before);
    }

    @Test
    void createSeats_existingInventory_isRejected_andNothingNewIsSaved() {
        UUID showId = UUID.randomUUID();
        UUID existingSeatId = UUID.randomUUID();
        showSeatInventoryService.createSeats(
                showId, List.of(new ShowSeatDefinition(existingSeatId, new BigDecimal("100.00"))));
        long afterFirstCall = showSeatRepository.count();

        assertThatThrownBy(() -> showSeatInventoryService.createSeats(showId, List.of(
                new ShowSeatDefinition(existingSeatId, new BigDecimal("999.00")))))
                .isInstanceOf(DuplicateShowSeatException.class)
                .hasMessageContaining(showId.toString())
                .hasMessageContaining(existingSeatId.toString());

        assertThat(showSeatRepository.count()).isEqualTo(afterFirstCall);
    }

    @Test
    void createSeats_existingInventoryOnADifferentShow_doesNotBlockTheSameSeatIdOnThisShow() {
        UUID otherShowId = UUID.randomUUID();
        UUID thisShowId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        showSeatInventoryService.createSeats(
                otherShowId, List.of(new ShowSeatDefinition(seatId, new BigDecimal("100.00"))));

        List<ShowSeat> created = showSeatInventoryService.createSeats(
                thisShowId, List.of(new ShowSeatDefinition(seatId, new BigDecimal("100.00"))));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getShowId()).isEqualTo(thisShowId);
    }

    @Test
    void createSeats_partialOverlapWithExistingInventory_rejectsTheWholeRequest_savesNothingNew() {
        UUID showId = UUID.randomUUID();
        UUID existingSeatId = UUID.randomUUID();
        UUID newSeatId = UUID.randomUUID();
        showSeatInventoryService.createSeats(
                showId, List.of(new ShowSeatDefinition(existingSeatId, new BigDecimal("100.00"))));
        long afterFirstCall = showSeatRepository.count();

        assertThatThrownBy(() -> showSeatInventoryService.createSeats(showId, List.of(
                new ShowSeatDefinition(newSeatId, new BigDecimal("100.00")),
                new ShowSeatDefinition(existingSeatId, new BigDecimal("100.00")))))
                .isInstanceOf(DuplicateShowSeatException.class);

        // The new, non-conflicting seat must NOT have been saved either —
        // all-or-nothing, not best-effort partial insertion.
        assertThat(showSeatRepository.count()).isEqualTo(afterFirstCall);
        assertThat(showSeatRepository.findByShowId(showId)).extracting(ShowSeat::getSeatId)
                .doesNotContain(newSeatId);
    }

    @Test
    void createSeats_emptyList_isRejected() {
        assertThatThrownBy(() -> showSeatInventoryService.createSeats(UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
