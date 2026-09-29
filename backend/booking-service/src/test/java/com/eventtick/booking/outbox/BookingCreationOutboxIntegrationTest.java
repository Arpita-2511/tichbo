package com.eventtick.booking.outbox;

import com.eventtick.booking.entity.Booking;
import com.eventtick.booking.entity.ShowSeat;
import com.eventtick.booking.entity.ShowSeatStatus;
import com.eventtick.booking.event.EventTopics;
import com.eventtick.booking.exception.InvalidSeatStateException;
import com.eventtick.booking.repository.BookingRepository;
import com.eventtick.booking.repository.ShowSeatRepository;
import com.eventtick.booking.service.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The "critical test" Phase 16 Step 2 asks for: {@code BookingService
 * #createBooking} writes its booking row and a {@code BookingCreated}
 * outbox row in the SAME database transaction, using the real repositories
 * and the real {@link OutboxService} — no mocks — against a real H2
 * schema (same {@code create-drop} pattern as {@code
 * OutboxEventRepositoryTest}, for the same reason).
 *
 * <p><b>Which failure this test uses, and why.</b> {@code createBooking}'s
 * outbox write is unconditionally its last statement — nothing in the
 * method can fail <i>after</i> it today, so there is no existing code path
 * that reaches the outbox write and then still rolls back. Fabricating one
 * (e.g. mocking {@link OutboxService} to throw) would test a scenario that
 * cannot actually happen, not the real behavior. Instead, {@link
 * #createBooking_failure_rollsBackBothTheBookingAndTheOutboxRow} uses the
 * failure point closest to the writes among {@code createBooking}'s real,
 * already-tested exception paths — "this seat already has an active
 * booking" — which fires after seats are locked and validated but before
 * the booking or outbox rows are ever written, and asserts neither exists
 * afterward. Combined with the success test below (both rows exist
 * together), this demonstrates the transaction is genuinely all-or-nothing,
 * not merely that failing early skips later statements.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BookingCreationOutboxIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    /**
     * {@code ShowSeat} has no public/business constructor — built the same
     * reflective way its own tests do. Seeded via {@code
     * showSeatRepository.saveAndFlush(...)}, not a raw {@code
     * EntityManager}: this test class is a plain JUnit object, not a
     * Spring-managed bean, so a {@code @Transactional} annotation on one of
     * its own methods would be inert (Spring AOP never proxies it) — the
     * repository's own inherited, already-transactional {@code save}
     * (§48/Phase 15 Step 3's own lesson about {@code @Modifying} methods
     * needing this doesn't apply to save/saveAndFlush, which already carry
     * it from {@code SimpleJpaRepository}) is what actually commits this
     * seed row in its own transaction before {@code createBooking} runs in
     * a separate one.
     */
    private ShowSeat heldSeat(UUID showId, BigDecimal price) {
        try {
            Constructor<ShowSeat> ctor = ShowSeat.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ShowSeat seat = ctor.newInstance();
            seat.setShowId(showId);
            seat.setSeatId(UUID.randomUUID());
            seat.setStatus(ShowSeatStatus.HELD);
            seat.setPrice(price);
            return showSeatRepository.saveAndFlush(seat);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createBooking_success_writesTheBookingAndItsOutboxRow_inOneTransaction() {
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ShowSeat seat = heldSeat(showId, new BigDecimal("500.00"));

        Booking booking = bookingService.createBooking(userId, showId, List.of(seat.getId()), "corr-abc");

        assertThat(bookingRepository.findById(booking.getId())).isPresent();
        List<OutboxEvent> rows = outboxEventRepository.findAll().stream()
                .filter(row -> row.getAggregateId().equals(booking.getId()))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventType()).isEqualTo("BookingCreated");
        assertThat(rows.get(0).getAggregateType()).isEqualTo("Booking");
        assertThat(rows.get(0).getTopic()).isEqualTo(EventTopics.BOOKING);
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void createBooking_success_outboxPayload_carriesTheBookingsOwnData_andTheGivenCorrelationId() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ShowSeat seat = heldSeat(showId, new BigDecimal("750.00"));

        Booking booking = bookingService.createBooking(userId, showId, List.of(seat.getId()), "corr-xyz");

        OutboxEvent row = outboxEventRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(booking.getId())).findFirst().orElseThrow();
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-xyz");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(booking.getId().toString());
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("bookingId").asText()).isEqualTo(booking.getId().toString());
        assertThat(payload.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("showId").asText()).isEqualTo(showId.toString());
        assertThat(payload.get("seatIds").get(0).asText()).isEqualTo(seat.getId().toString());
        assertThat(payload.get("totalAmount").decimalValue()).isEqualByComparingTo("750.00");
    }

    @Test
    void createBooking_failure_rollsBackBothTheBookingAndTheOutboxRow() {
        UUID showId = UUID.randomUUID();
        ShowSeat seat = heldSeat(showId, new BigDecimal("500.00"));
        long bookingsBefore = bookingRepository.count();
        long outboxRowsBefore = outboxEventRepository.count();

        // Book it once successfully first, so the seat now has an active
        // (PENDING) booking...
        bookingService.createBooking(UUID.randomUUID(), showId, List.of(seat.getId()), "corr-1");
        long bookingsAfterFirst = bookingRepository.count();
        long outboxRowsAfterFirst = outboxEventRepository.count();
        assertThat(bookingsAfterFirst).isEqualTo(bookingsBefore + 1);
        assertThat(outboxRowsAfterFirst).isEqualTo(outboxRowsBefore + 1);

        // ...so a second attempt on the SAME seat fails, after seats are
        // locked and validated but before either write happens.
        assertThatThrownBy(() -> bookingService.createBooking(UUID.randomUUID(), showId, List.of(seat.getId()), "corr-2"))
                .isInstanceOf(InvalidSeatStateException.class);

        assertThat(bookingRepository.count()).isEqualTo(bookingsAfterFirst);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxRowsAfterFirst);
    }

    @Test
    void createBooking_succeeds_evenThoughNoKafkaBrokerIsReachableAtAll() {
        // This Spring context's own bootstrap-servers (inherited from
        // src/test/resources/application.yml) points at localhost:1 — an
        // address nothing is listening on. createBooking/OutboxService have
        // no KafkaTemplate dependency at all (only OutboxPublisher does,
        // and nothing here calls it), so this succeeding at all is the
        // proof: booking creation cannot be affected by Kafka's
        // availability, because it never talks to Kafka.
        UUID showId = UUID.randomUUID();
        ShowSeat seat = heldSeat(showId, new BigDecimal("500.00"));

        Booking booking = bookingService.createBooking(UUID.randomUUID(), showId, List.of(seat.getId()), "corr-no-kafka");

        assertThat(bookingRepository.findById(booking.getId())).isPresent();
        assertThat(outboxEventRepository.findAll().stream().anyMatch(r -> r.getAggregateId().equals(booking.getId())))
                .isTrue();
    }
}
