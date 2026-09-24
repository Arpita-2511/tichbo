package com.eventtick.user.service;

import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
import com.eventtick.user.exception.DuplicateEmailException;
import com.eventtick.user.exception.InvalidCredentialsException;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
import com.eventtick.user.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Password hashing via {@link PasswordEncoder}
 * (BCrypt — see {@code SecurityConfig}) and JWT issuance via
 * {@link JwtService} both happen here, not in the controller.
 *
 * <p>The default plan for a new registration is looked up by name
 * ("Free") rather than hardcoding its seeded UUID — the seed data in
 * {@code database/migrations/0003_seed_plans.up.sql} already gives it a
 * fixed, well-known id specifically so application code *could* reference
 * it as a constant, but looking it up by name here avoids embedding a
 * magic UUID literal in Java source, at the cost of one extra query per
 * registration. If "Free" doesn't exist (a seed-data/deployment problem,
 * not a client error), registration fails with an unhandled
 * {@link IllegalStateException} — deliberately not caught by
 * {@code GlobalExceptionHandler}, since a 500 is the honest status for a
 * genuine server misconfiguration.
 */
@Service
public class AuthService {

    private static final String DEFAULT_PLAN_NAME = "Free";

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PlanRepository planRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * @throws DuplicateEmailException if the (lowercased) email is already registered
     */
    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);

        // Friendly pre-check; the DB's uq_users_email constraint remains
        // the actual authority and catches the race two concurrent
        // registrations for the same email would otherwise hit (see
        // GlobalExceptionHandler's DataIntegrityViolationException handler).
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        Plan defaultPlan = planRepository.findByName(DEFAULT_PLAN_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Default plan '" + DEFAULT_PLAN_NAME + "' not found — check plan seed data."));

        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.CUSTOMER);
        user.setPlan(defaultPlan);

        return userRepository.save(user);
    }

    /**
     * @throws InvalidCredentialsException if the email doesn't match a
     *         user or the password doesn't match — deliberately
     *         indistinguishable to the caller (see that exception's Javadoc)
     */
    @Transactional(readOnly = true)
    public AuthResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new AuthResult(user, token, jwtService.getExpirationSeconds());
    }

    /**
     * The database does not enforce email casing (see {@code User.email}'s
     * Javadoc) — this is the one place that normalization must happen for
     * both storage (register) and lookup (register's duplicate check, login).
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
