package com.eventtick.user;

import com.eventtick.user.config.AdminBootstrap;
import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AdminBootstrap}. Uses Mockito (provided by
 * spring-boot-starter-test) to isolate from the database and to
 * control environment variable values via a subclass override.
 */
class AdminBootstrapTest {

    private UserRepository userRepository;
    private PlanRepository planRepository;
    private PasswordEncoder passwordEncoder;

    private Plan freePlan;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        planRepository = mock(PlanRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        freePlan = new Plan();
        freePlan.setName("Free");
        freePlan.setPrice(BigDecimal.ZERO);
        freePlan.setDescription("Free plan");

        when(planRepository.findByName("Free")).thenReturn(Optional.of(freePlan));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt(" + inv.getArgument(0) + ")");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AdminBootstrap bootstrap(Map<String, String> env) {
        return new AdminBootstrap(userRepository, planRepository, passwordEncoder) {
            @Override
            protected String getEnv(String name) {
                return env.get(name);
            }
        };
    }

    @Test
    void missingEnvVars_doesNothing() throws Exception {
        bootstrap(Map.of()).run();

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void blankEmail_doesNothing() throws Exception {
        bootstrap(Map.of("ADMIN_EMAIL", "  ", "ADMIN_PASSWORD", "secret")).run();

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void blankPassword_doesNothing() throws Exception {
        bootstrap(Map.of("ADMIN_EMAIL", "admin@test.com", "ADMIN_PASSWORD", "")).run();

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void existingUser_doesNotCreateNew() throws Exception {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(new User()));

        bootstrap(Map.of("ADMIN_EMAIL", "admin@test.com", "ADMIN_PASSWORD", "secret")).run();

        verify(userRepository).findByEmail("admin@test.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void newAdmin_createdWithCorrectFields() throws Exception {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        bootstrap(Map.of("ADMIN_EMAIL", "Admin@Test.COM", "ADMIN_PASSWORD", "my-secret")).run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("Admin", saved.getName());
        assertEquals("admin@test.com", saved.getEmail());
        assertEquals(UserRole.ADMIN, saved.getRole());
        assertEquals(freePlan, saved.getPlan());
        assertEquals("bcrypt(my-secret)", saved.getPasswordHash());
    }

    @Test
    void passwordNeverStoredRaw() throws Exception {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        bootstrap(Map.of("ADMIN_EMAIL", "admin@test.com", "ADMIN_PASSWORD", "raw-secret")).run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertNotEquals("raw-secret", captor.getValue().getPasswordHash());
        verify(passwordEncoder).encode("raw-secret");
    }

    @Test
    void emailNormalized_toLowercase() throws Exception {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        bootstrap(Map.of("ADMIN_EMAIL", "  Admin@TEST.COM  ", "ADMIN_PASSWORD", "secret")).run();

        verify(userRepository).findByEmail("admin@test.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("admin@test.com", captor.getValue().getEmail());
    }
}
