package com.eventtick.user.service;

import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
import com.eventtick.user.exception.PlanNotFoundException;
import com.eventtick.user.exception.SelfRoleModificationException;
import com.eventtick.user.exception.UserNotFoundException;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * User lookups plus the FR-37 admin write operations, separate from
 * {@link AuthService} — same split as booking-service's
 * {@code ShowSeatQueryService} vs {@code BookingService} (reads/admin
 * operations vs. registration/login-specific logic).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    public UserService(UserRepository userRepository, PlanRepository planRepository) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    /**
     * @throws UserNotFoundException if id doesn't exist — in practice only
     *         reachable if a valid JWT outlives the user it names (e.g.
     *         the account was deleted after the token was issued); no
     *         endpoint accepts a client-supplied id yet.
     */
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * For {@code GET /api/admin/users} (Phase 13.4) — no admin-only check
     * here; that is enforced once, at the gateway
     * ({@code /api/admin/** -> ROLE_ADMIN}), the same boundary every other
     * admin-only path relies on.
     */
    @Transactional(readOnly = true)
    public Page<User> listAll(Pageable pageable) {
        return userRepository.findAllBy(pageable);
    }

    /**
     * {@code PATCH /api/admin/users/{userId}/plan} (Phase 13, FR-37). A
     * plain FK reassignment — no billing, proration, or tier business
     * rules exist for this (see the Phase 13 FR-37 inspection report);
     * the only requirement is that {@code planId} names a real plan.
     *
     * @throws UserNotFoundException if userId doesn't exist
     * @throws PlanNotFoundException if planId doesn't exist
     */
    @Transactional
    public User changePlan(UUID userId, UUID planId) {
        User user = getById(userId);
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
        user.setPlan(plan);
        return userRepository.save(user);
    }

    /**
     * {@code PATCH /api/admin/users/{userId}/role} (Phase 13, FR-37).
     * {@code requestingAdminId} is the caller's own id, taken from their
     * validated JWT ({@code Authentication.getName()} in the controller)
     * — never a client-supplied value — so this self-check cannot be
     * bypassed by a forged body. An administrator may change any other
     * user's role in either direction (CUSTOMER -> ADMIN, ADMIN ->
     * CUSTOMER) but not their own. No "last ADMIN" rule: not specified by
     * FR-37, so not enforced here.
     *
     * @throws SelfRoleModificationException if userId equals requestingAdminId
     * @throws UserNotFoundException if userId doesn't exist
     */
    @Transactional
    public User changeRole(UUID userId, UserRole newRole, UUID requestingAdminId) {
        if (userId.equals(requestingAdminId)) {
            throw new SelfRoleModificationException(userId);
        }
        User user = getById(userId);
        user.setRole(newRole);
        return userRepository.save(user);
    }
}
