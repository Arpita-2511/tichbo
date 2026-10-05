package com.eventtick.booking.dto;

import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 22: SeatMapItemDto.from() lazy expiration — a HELD seat whose
 * hold has expired should be reported as AVAILABLE in the DTO.
 */
class SeatMapItemDtoTest {

    private ShowSeat seat(ShowSeatStatus status, Instant holdExpiresAt) {
        ShowSeat seat = new ShowSeat();
        ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
        seat.setSeatId(UUID.randomUUID());
        seat.setShowId(UUID.randomUUID());
        seat.setStatus(status);
        seat.setPrice(new BigDecimal("100.00"));
        seat.setHoldExpiresAt(holdExpiresAt);
        return seat;
    }

    @Test
    void from_availableSeat_staysAvailable() {
        SeatMapItemDto dto = SeatMapItemDto.from(seat(ShowSeatStatus.AVAILABLE, null));
        assertThat(dto.status()).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void from_heldSeat_notExpired_staysHeld() {
        SeatMapItemDto dto = SeatMapItemDto.from(seat(ShowSeatStatus.HELD, Instant.now().plusSeconds(600)));
        assertThat(dto.status()).isEqualTo(ShowSeatStatus.HELD);
    }

    @Test
    void from_heldSeat_expired_becomesAvailable() {
        SeatMapItemDto dto = SeatMapItemDto.from(seat(ShowSeatStatus.HELD, Instant.now().minusSeconds(1)));
        assertThat(dto.status()).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void from_bookedSeat_staysBooked() {
        SeatMapItemDto dto = SeatMapItemDto.from(seat(ShowSeatStatus.BOOKED, null));
        assertThat(dto.status()).isEqualTo(ShowSeatStatus.BOOKED);
    }

    @Test
    void from_heldSeat_nullExpiresAt_staysHeld() {
        SeatMapItemDto dto = SeatMapItemDto.from(seat(ShowSeatStatus.HELD, null));
        assertThat(dto.status()).isEqualTo(ShowSeatStatus.HELD);
    }
}
