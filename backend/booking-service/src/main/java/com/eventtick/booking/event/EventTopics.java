package com.eventtick.booking.event;

/**
 * The four approved Kafka topics (docs/architecture.md §47.4 / §27
 * requirements.md) — one per owning domain, not one per event type. Plain
 * constants, not a derivation function: only one producer
 * (booking-service, publishing to {@link #BOOKING}) exists as of this
 * step, so a generic aggregate-type-to-topic mapping would be premature
 * abstraction for a mapping with exactly one real entry. Every caller
 * names its own topic explicitly.
 *
 * <p>Topics are provisioned as infrastructure (see {@code kafka/README.md}),
 * never created by application startup code — this class only names them.
 */
public final class EventTopics {

    public static final String BOOKING = "eventtick.booking";
    public static final String PAYMENT = "eventtick.payment";
    public static final String CATALOG = "eventtick.catalog";
    public static final String USER = "eventtick.user";

    private EventTopics() {
    }
}
