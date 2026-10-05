package com.eventtick.user.config;

import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-time bootstrap for the first ADMIN account. Reads {@code ADMIN_EMAIL}
 * and {@code ADMIN_PASSWORD} from environment variables on startup:
 *
 * <ul>
 *   <li>Both set and no user with that email exists → creates an ADMIN user
 *       on the Free plan.</li>
 *   <li>Either variable missing/blank, or a user with that email already
 *       exists → does nothing (idempotent, no error).</li>
 * </ul>
 *
 * <p>After the first admin exists they can promote others via
 * {@code PATCH /api/admin/users/{userId}/role}; this bootstrap is only
 * needed once to break the chicken-and-egg cycle.
 *
 * <p><b>Security:</b> the raw password is never logged or persisted — only
 * the BCrypt hash reaches the database, same path as
 * {@link com.eventtick.user.service.AuthService#register}.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String DEFAULT_PLAN_NAME = "Free";

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository,
                          PlanRepository planRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String email = getEnv("ADMIN_EMAIL");
        String password = getEnv("ADMIN_PASSWORD");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            log.info("Bootstrap: admin account for {} already exists, skipping.", normalizedEmail);
            return;
        }

        Plan freePlan = planRepository.findByName(DEFAULT_PLAN_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Default plan '" + DEFAULT_PLAN_NAME + "' not found — check plan seed data."));

        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setPlan(freePlan);
        userRepository.save(admin);

        log.info("Bootstrap: created admin account for {}.", normalizedEmail);
    }

    protected String getEnv(String name) {
        return System.getenv(name);
    }
}
