package com.eventtick.user;

import com.eventtick.user.dto.RegisterRequest;
import com.eventtick.user.entity.Plan;
import com.eventtick.user.repository.PlanRepository;
import com.eventtick.user.repository.UserRepository;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.4: {@code GET /api/admin/users} — the endpoint's own correctness
 * (pagination, the {@code plan} entity graph, response safety), not the
 * {@code role=ADMIN} boundary itself.
 *
 * <p>That boundary is enforced once, at the gateway
 * (Phase 13.3, {@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}), and is
 * already exhaustively covered by gateway-service's
 * {@code GatewayAdminAuthorizationTest} (ADMIN -> 200 and reaches the
 * upstream, CUSTOMER -> 403 with the standard body, missing/invalid JWT ->
 * 401). user-service itself has <b>no role check of its own</b> for this
 * endpoint — {@code UserController.listUsers}/{@code UserService.listAll}
 * accept any authenticated caller, by the same "authorize once, at the
 * edge" design every other admin-only path in this project uses. Asserting
 * a 403-for-CUSTOMER *here* would be both redundant with that gateway test
 * and actually false at this layer (a valid CUSTOMER token gets 200 from
 * user-service directly), so this class deliberately uses a normal,
 * self-registered account's own token throughout — the endpoint doesn't
 * care whose token it is, only whether the JWT is valid, same requirement
 * as every other authenticated endpoint in this service.
 *
 * <p>Same real-HTTP/real-H2 style as {@link AuthenticationIntegrationTest},
 * for the same reason given there: a lazy-loading bug (exactly what this
 * class's test B guards against) only shows up outside an open Hibernate
 * session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminUserListIntegrationTest {

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

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        planRepository.deleteAll();
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, "correct-password"))));
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.eventtick.user.dto.LoginRequest(email, "correct-password"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    // ---- A: paginated users are returned ----

    @Test
    void returnsAPageOfUsers_withPaginationMetadata() throws Exception {
        registerUser("alice@example.com");
        registerUser("bob@example.com");
        registerUser("carol@example.com");
        String token = loginAndGetToken("alice@example.com");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20)); // @PageableDefault
    }

    @Test
    void pageAndSizeQueryParameters_areHonored() throws Exception {
        registerUser("one@example.com");
        registerUser("two@example.com");
        registerUser("three@example.com");
        String token = loginAndGetToken("one@example.com");

        mockMvc.perform(get("/api/admin/users?page=0&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/admin/users?page=1&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void aPageBeyondTheLastResult_isAnEmptyPage_not404OrError() throws Exception {
        registerUser("only-caller@example.com");
        String token = loginAndGetToken("only-caller@example.com");

        // Only one user exists (the caller), so page 5 is past the end —
        // an empty content list with a 200, not an error.
        mockMvc.perform(get("/api/admin/users?page=5&size=20").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- B: plan information loads correctly, no LazyInitializationException ----

    @Test
    void planInformation_isLoadedForEveryUser_withoutLazyInitializationException() throws Exception {
        registerUser("withplan1@example.com");
        registerUser("withplan2@example.com");
        String token = loginAndGetToken("withplan1@example.com");

        // If UserRepository.findAllBy(Pageable) were missing its
        // @EntityGraph, reading user.getPlan().getName() while mapping to
        // UserResponse would throw LazyInitializationException here (the
        // Hibernate session closes when the @Transactional service method
        // returns, before this MockMvc call ever sees the response) — this
        // would surface as a 500, not a passing request.
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].planName").value("Free"))
                .andExpect(jsonPath("$.content[0].planId").exists())
                .andExpect(jsonPath("$.content[1].planName").value("Free"));
    }

    // ---- C: no credential field is ever present ----

    @Test
    void theResponse_neverContainsPasswordHashOrAnyCredentialField() throws Exception {
        registerUser("secret1@example.com");
        registerUser("secret2@example.com");
        String token = loginAndGetToken("secret1@example.com");

        String body = mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("passwordHash").doesNotContainIgnoringCase("password");
        // And the safe fields ARE present, per the response contract.
        assertThat(body).contains("\"id\"").contains("\"name\"").contains("\"email\"")
                .contains("\"role\"").contains("\"planId\"").contains("\"planName\"")
                .contains("\"createdAt\"").contains("\"updatedAt\"");
    }

    @Test
    void roleAndPlan_areVisibleInTheResponse() throws Exception {
        // Explicit coverage that an admin-facing response includes both —
        // per the endpoint contract (docs/api-contracts.md).
        registerUser("rolecheck@example.com");
        String token = loginAndGetToken("rolecheck@example.com");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.content[0].planName").value("Free"));
    }

    // ---- authentication is still required (unchanged general endpoint behavior) ----

    @Test
    void withoutAToken_returns401_sameAsEveryOtherProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }
}
