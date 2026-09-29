package com.eventtick.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick Audit Service (Phase 16 Step 3).
 *
 * <p>Owns exactly one table, {@code booking_event_audit} — a persistent,
 * idempotent projection of domain events consumed from Kafka, currently
 * just {@code BookingCreated} from {@code eventtick.booking}. Owns no
 * booking/payment/catalog/user domain state; see
 * {@code docs/architecture.md} §49 for the full reasoning behind this
 * being a separate, dedicated service.
 */
@SpringBootApplication
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
