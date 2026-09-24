package com.eventtick.user.service;

import com.eventtick.user.entity.User;
import com.eventtick.user.exception.UserNotFoundException;
import com.eventtick.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only user lookups, separate from {@link AuthService} — same split
 * as booking-service's {@code ShowSeatQueryService} vs {@code BookingService}
 * (reads vs. writes/auth-specific operations).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
