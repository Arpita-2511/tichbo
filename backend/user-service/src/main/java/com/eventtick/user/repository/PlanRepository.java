package com.eventtick.user.repository;

import com.eventtick.user.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /** Used by {@code AuthService} to resolve the default ("Free") plan at registration. */
    Optional<Plan> findByName(String name);
}
