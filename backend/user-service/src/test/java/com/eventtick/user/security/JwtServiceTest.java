package com.eventtick.user.security;

import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — no Spring context, no database. Covers the JWT
 * scenarios the project explicitly asks for: valid, expired, invalid
 * signature, malformed. ("Missing" and, via HTTP, "malformed" are
 * exercised end to end in {@code AuthenticationIntegrationTest} instead.)
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-secret-used-for-jwtservicetest-must-be-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET, "eventtick-user-service", "eventtick-clients", 60);

    /**
     * {@code JwtService.generateToken} requires {@code user.getId()} — a
     * real {@link User} only ever has one after being persisted (id is
     * {@code @GeneratedValue}, no public setter, by design; see
     * {@code User}'s Javadoc). This fixture is never persisted, so its id
     * is set directly via {@link ReflectionTestUtils} — the standard way
     * to give a transient test object a generated-looking id without
     * either touching the database or adding a production setter that
     * would let application code assign ids itself.
     */
    private User sampleUser() {
        Plan plan = new Plan();
        plan.setName("Free");
        // JwtService only reads plan.getName() when building a token, so
        // Plan's id being unset (same reasoning as User's, but Plan's id
        // is never actually read) doesn't matter here.

        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setRole(UserRole.CUSTOMER);
        user.setPlan(plan);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void generateAndParseToken_roundTrips() {
        User user = sampleUser();

        String token = jwtService.generateToken(user);
        Optional<JwtAuthentication> parsed = jwtService.parseToken(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().email()).isEqualTo("ada@example.com");
        assertThat(parsed.get().role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(parsed.get().plan()).isEqualTo("Free");
    }

    @Test
    void parseToken_expiredToken_isRejected() {
        // A JwtService whose tokens are already expired the instant they're issued.
        JwtService expiredIssuer = new JwtService(SECRET, "eventtick-user-service", "eventtick-clients", -1);
        String token = expiredIssuer.generateToken(sampleUser());

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_wrongSigningKey_isRejected() {
        JwtService otherIssuer = new JwtService(
                "a-completely-different-secret-that-is-also-at-least-32-bytes-long",
                "eventtick-user-service", "eventtick-clients", 60);
        String token = otherIssuer.generateToken(sampleUser());

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_wrongIssuerOrAudience_isRejected() {
        JwtService otherAudience = new JwtService(SECRET, "eventtick-user-service", "a-different-audience", 60);
        String token = otherAudience.generateToken(sampleUser());

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    void parseToken_malformedToken_isRejected() {
        assertThat(jwtService.parseToken("not-a-jwt-at-all")).isEmpty();
        assertThat(jwtService.parseToken("")).isEmpty();
    }
}
