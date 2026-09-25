package com.eventtick.user;

import com.eventtick.user.dto.LoginRequest;
import com.eventtick.user.dto.RegisterRequest;
import com.eventtick.user.entity.Plan;
import com.eventtick.user.entity.User;
import com.eventtick.user.entity.UserRole;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13, FR-37: {@code PATCH /api/admin/users/{userId}/plan} and
 * {@code PATCH /api/admin/users/{userId}/role} — the endpoints' own
 * correctness, not the {@code role=ADMIN} gateway boundary itself (already
 * exhaustively covered by {@code GatewayAdminAuthorizationTest}). Same
 * real-HTTP/real-H2 style as {@link AdminUserListIntegrationTest}, for the
 * same reason: a lazy-loading bug around {@code Plan} only shows up
 * outside an open Hibernate session.
 *
 * <p>Also covers FR-36's {@code GET /api/admin/users/stats} — placed here
 * rather than a new class since it's the same controller
 * ({@code AdminUserController}) and the same test style.
 *
 * <p>Like {@link AdminUserListIntegrationTest}, this class deliberately
 * uses ordinary self-registered accounts' own tokens throughout — the
 * endpoint doesn't check the caller's role itself (that's the Gateway's
 * job), only that the JWT is valid. For role-change tests, "the acting
 * admin" and "the target user" are simply two different registered
 * accounts; what matters is that {@code Authentication.getName()} (the
 * acting account's own id, from its own token) differs from the
 * {@code userId} path variable (the target), exactly as the self-role
 * check itself compares them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminUserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID proPlanId;
    private UUID premiumPlanId;

    @BeforeEach
    void seedPlans() {
        savePlan("Free", BigDecimal.ZERO);
        proPlanId = savePlan("Pro", new BigDecimal("199.00")).getId();
        premiumPlanId = savePlan("Premium", new BigDecimal("499.00")).getId();
    }

    private Plan savePlan(String name, BigDecimal price) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setPrice(price);
        plan.setDescription("Test fixture");
        return planRepository.save(plan);
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
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "correct-password"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // ==================== Plan ====================

    @Test
    void changePlan_customerToPro_updatesPlanIdAndPlanName() throws Exception {
        registerUser("caller@example.com");
        registerUser("plantarget1@example.com");
        String token = loginAndGetToken("caller@example.com");
        UUID targetId = idOf("plantarget1@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/plan", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(proPlanId.toString()))
                .andExpect(jsonPath("$.planName").value("Pro"));
    }

    @Test
    void changePlan_customerToPremium_updatesPlanIdAndPlanName() throws Exception {
        registerUser("caller@example.com");
        registerUser("plantarget2@example.com");
        String token = loginAndGetToken("caller@example.com");
        UUID targetId = idOf("plantarget2@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/plan", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + premiumPlanId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(premiumPlanId.toString()))
                .andExpect(jsonPath("$.planName").value("Premium"));
    }

    @Test
    void changePlan_unknownUser_returns404UserNotFound() throws Exception {
        registerUser("caller@example.com");
        String token = loginAndGetToken("caller@example.com");
        UUID unknownUserId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/{userId}/plan", unknownUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void changePlan_unknownPlan_returns404PlanNotFound() throws Exception {
        registerUser("caller@example.com");
        registerUser("plantarget3@example.com");
        String token = loginAndGetToken("caller@example.com");
        UUID targetId = idOf("plantarget3@example.com");
        UUID unknownPlanId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/{userId}/plan", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + unknownPlanId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLAN_NOT_FOUND"));
    }

    @Test
    void changePlan_persistedChange_survivesASubsequentRead() throws Exception {
        registerUser("caller@example.com");
        registerUser("plantarget4@example.com");
        String token = loginAndGetToken("caller@example.com");
        UUID targetId = idOf("plantarget4@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/plan", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isOk());

        // A fresh read (GET /api/admin/users) proves the change was
        // actually persisted, not just reflected in the write response.
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + targetId + "')].planName").value("Pro"));
    }

    // ==================== Role ====================

    @Test
    void changeRole_customerToAdmin_updatesRole() throws Exception {
        registerUser("actor1@example.com");
        registerUser("roletarget1@example.com");
        String actorToken = loginAndGetToken("actor1@example.com");
        UUID targetId = idOf("roletarget1@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/role", targetId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void changeRole_adminToCustomer_forAnotherUser_updatesRole() throws Exception {
        registerUser("actor2@example.com");
        registerUser("roletarget2@example.com");
        String actorToken = loginAndGetToken("actor2@example.com");
        UUID targetId = idOf("roletarget2@example.com");
        // Start the target as ADMIN so this exercises the ADMIN -> CUSTOMER direction.
        User target = userRepository.findById(targetId).orElseThrow();
        target.setRole(UserRole.ADMIN);
        userRepository.save(target);

        mockMvc.perform(patch("/api/admin/users/{userId}/role", targetId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void changeRole_unknownUser_returns404UserNotFound() throws Exception {
        registerUser("actor3@example.com");
        String actorToken = loginAndGetToken("actor3@example.com");
        UUID unknownUserId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/{userId}/role", unknownUserId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void changeRole_invalidRoleValue_returns400ValidationError() throws Exception {
        registerUser("actor4@example.com");
        registerUser("roletarget4@example.com");
        String actorToken = loginAndGetToken("actor4@example.com");
        UUID targetId = idOf("roletarget4@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/role", targetId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void changeRole_missingRoleField_returns400ValidationError() throws Exception {
        registerUser("actor5@example.com");
        registerUser("roletarget5@example.com");
        String actorToken = loginAndGetToken("actor5@example.com");
        UUID targetId = idOf("roletarget5@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/role", targetId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==================== Self-role ====================

    @Test
    void changeRole_targetingOwnUserId_isRejected_withSelfRoleModificationNotAllowed() throws Exception {
        registerUser("selfactor@example.com");
        String actorToken = loginAndGetToken("selfactor@example.com");
        UUID ownId = idOf("selfactor@example.com");

        mockMvc.perform(patch("/api/admin/users/{userId}/role", ownId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_ROLE_MODIFICATION_NOT_ALLOWED"));

        // And the role was genuinely not changed.
        User self = userRepository.findById(ownId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(self.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    // ==================== Regression ====================

    @Test
    void getUsersMe_stillWorks_afterAdminUserWriteEndpointsExist() throws Exception {
        registerUser("regression-me@example.com");
        String token = loginAndGetToken("regression-me@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("regression-me@example.com"));
    }

    @Test
    void getAdminUsers_stillWorks_afterAdminUserWriteEndpointsExist() throws Exception {
        registerUser("regression-list@example.com");
        String token = loginAndGetToken("regression-list@example.com");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // ==================== Stats (FR-36) ====================

    @Test
    void stats_validRequest_returns200() throws Exception {
        registerUser("statscaller1@example.com");
        String token = loginAndGetToken("statscaller1@example.com");

        mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void stats_responseContainsTotalUsers() throws Exception {
        registerUser("statscaller2@example.com");
        String token = loginAndGetToken("statscaller2@example.com");

        mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").exists());
    }

    @Test
    void stats_reflectsThePersistedUserCount() throws Exception {
        registerUser("statscaller3@example.com");
        registerUser("statsother1@example.com");
        registerUser("statsother2@example.com");
        String token = loginAndGetToken("statscaller3@example.com");

        // 3 registrations above -> 3 persisted users.
        mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3));
    }

    @Test
    void stats_emptyDatabase_returnsZero() throws Exception {
        // A valid JWT doesn't require the user it names to still exist
        // (JwtAuthenticationFilter never re-checks the database) — register
        // and log in to get a real token, then delete that user, leaving
        // the table genuinely empty for the call itself.
        registerUser("statscaller4@example.com");
        String token = loginAndGetToken("statscaller4@example.com");
        userRepository.deleteAll();

        mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(0));
    }

    @Test
    void stats_doesNotExposePasswordHashOrUnrelatedUserData() throws Exception {
        registerUser("statscaller5@example.com");
        String token = loginAndGetToken("statscaller5@example.com");

        String body = mockMvc.perform(get("/api/admin/users/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContainIgnoringCase("password")
                .doesNotContain("email")
                .doesNotContain("\"id\"")
                .doesNotContain("\"role\"")
                .doesNotContain("\"planId\"");
        // Exactly the one field the response shape is meant to expose.
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(json.size()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(json.fieldNames().next()).isEqualTo("totalUsers");
    }
}
