package com.eventtick.audit.repository;

import com.eventtick.audit.entity.PaymentEventAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data JPA repository for {@link PaymentEventAudit}. */
public interface PaymentEventAuditRepository extends JpaRepository<PaymentEventAudit, UUID> {
}
