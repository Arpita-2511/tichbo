package com.eventtick.audit.repository;

import com.eventtick.audit.entity.BookingEventAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data JPA repository for {@link BookingEventAudit}. */
public interface BookingEventAuditRepository extends JpaRepository<BookingEventAudit, UUID> {
}
