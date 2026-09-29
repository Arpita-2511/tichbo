package com.eventtick.payment.event;

/**
 * Mirrors booking-service's own {@code com.eventtick.booking.event.EventTopics}
 * and audit-service's own copy — the four approved Kafka topics
 * (docs/architecture.md §47.4/§27 requirements.md). Duplicated, not shared
 * via a common module (no shared Maven module exists in this project — see
 * docs/architecture.md §49.1); only {@link #PAYMENT} is actually published
 * to by this service today.
 */
public final class EventTopics {

    public static final String BOOKING = "eventtick.booking";
    public static final String PAYMENT = "eventtick.payment";
    public static final String CATALOG = "eventtick.catalog";
    public static final String USER = "eventtick.user";

    private EventTopics() {
    }
}
