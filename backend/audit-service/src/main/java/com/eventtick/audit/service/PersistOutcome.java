package com.eventtick.audit.service;

/** What {@link BookingEventAuditService#persist} actually did — tells the consumer whether to acknowledge. */
public enum PersistOutcome {
    /** A new row was inserted. First time this eventId has been seen. */
    PERSISTED,
    /** {@code eventId} already exists — a genuine duplicate delivery, safely recognized and ignored. */
    DUPLICATE
}
