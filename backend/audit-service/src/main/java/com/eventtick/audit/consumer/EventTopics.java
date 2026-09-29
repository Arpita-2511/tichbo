package com.eventtick.audit.consumer;

/**
 * Mirrors booking-service's own {@code com.eventtick.booking.event.EventTopics}
 * — the four approved Kafka topics (docs/architecture.md §47.4/§27
 * requirements.md). Duplicated, not shared via a common module (§49.1: no
 * shared Maven module exists in this project); only {@link #BOOKING} is
 * actually consumed by this service today.
 */
public final class EventTopics {

    public static final String BOOKING = "eventtick.booking";
    public static final String PAYMENT = "eventtick.payment";
    public static final String CATALOG = "eventtick.catalog";
    public static final String USER = "eventtick.user";

    private EventTopics() {
    }
}
