package com.eventtick.user;

import com.eventtick.user.dto.LoginRequest;
import com.eventtick.user.dto.RegisterRequest;
import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the authentication flow through real HTTP
 * (MockMvc), Spring Security's actual filter chain, and a real (H2,
 * schema-generated — see {@code src/test/resources/application.yml})
 * database.
 *
 * <p>The "Free" plan is seeded here as test fixture data via
 * {@link PlanRepository}, not via a migration — this is not the "no fake
 * data" the project rules mean (that's about the real Postgres database);
 * a test needs its own fixtures against its own ephemeral database.
 *
 * <p>Deliberately NOT {@code @Transactional}: a test-wide transaction
 * would keep one Hibernate session open across the whole request, hiding
 * lazy-loading bugs that only appear in production (where each service
 * call has its own short transaction and {@code open-in-view} is off).
 * That is exactly how a {@code LazyInitializationException} in
 * {@code GET /api/users/me} slipped past an earlier version of this
 * class. Data is committed for real, so {@link #cleanUp()} removes it
 * after each test.
 *
 * <p>Complements {@code JwtServiceTest}, which covers expired/wrong-signature
 * tokens in isolation without needing the full stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedFreePlan() {
        Plan plan = new Plan();
        plan.setName("Free");
        plan.setPrice(BigDecimal.ZERO);
        plan.setDescription("Test fixture");
        planRepository.save(plan);
    }

    /** Users first — {@code users.plan_id} references {@code plans}. */
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        planRepository.deleteAll();
    }

    @Test
    void register_validRequest_createsCustomerOnFreePlan() throws Exception {
        RegisterRequest request = new RegisterRequest("Ada Lovelace", "Ada@Example.com", "correct-password");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com")) // normalized to lowercase
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.planName").value("Free"))
                .andExpect(jsonPath("$.id").exists());

        Optional<User> saved = userRepository.findByEmail("ada@example.com");
        assertThat(saved).isPresent();
        assertThat(saved.get().getPasswordHash()).isNotEqualTo("correct-password");
        assertThat(saved.get().getPasswordHash()).startsWith("$2"); // BCrypt hash prefix
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("Ada", "dup@example.com", "correct-password");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("Ada", "not-an-email", "correct-password");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void register_blankName_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("", "ada@example.com", "correct-password");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("Ada", "ada@example.com", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returnsJwt() throws Exception {
        registerUser("login@example.com", "correct-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("login@example.com", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerUser("wrongpass@example.com", "correct-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("wrongpass@example.com", "totally-wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void login_unknownEmail_returns401WithSameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@example.com", "whatever12"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void me_withValidToken_returnsCurrentUser() throws Exception {
        registerUser("me@example.com", "correct-password");
        String token = loginAndGetToken("me@example.com", "correct-password");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void me_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, password))));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }
}
