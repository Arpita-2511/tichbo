package com.eventtick.user;

import com.eventtick.user.dto.LoginRequest;
import com.eventtick.user.dto.RegisterRequest;
import com.eventtick.user.entity.Plan;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CustomerPlanIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlanRepository planRepository;
    @Autowired private UserRepository userRepository;

    private UUID freePlanId;
    private UUID proPlanId;
    private UUID premiumPlanId;

    @BeforeEach
    void seedPlans() {
        freePlanId = savePlan("Free", BigDecimal.ZERO, "Basic access").getId();
        proPlanId = savePlan("Pro", new BigDecimal("199.00"), "Enhanced access").getId();
        premiumPlanId = savePlan("Premium", new BigDecimal("499.00"), "Full access").getId();
    }

    private Plan savePlan(String name, BigDecimal price, String description) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setPrice(price);
        plan.setDescription(description);
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

    // ==================== GET /api/users/plans ====================

    @Test
    void listPlans_returnsAllPlans_orderedByPriceAsc() throws Exception {
        registerUser("planlist1@example.com");
        String token = loginAndGetToken("planlist1@example.com");

        String body = mockMvc.perform(get("/api/users/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode plans = objectMapper.readTree(body);
        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).get("name").asText()).isEqualTo("Free");
        assertThat(plans.get(0).get("price").asDouble()).isEqualTo(0.0);
        assertThat(plans.get(1).get("name").asText()).isEqualTo("Pro");
        assertThat(plans.get(1).get("price").asDouble()).isEqualTo(199.0);
        assertThat(plans.get(2).get("name").asText()).isEqualTo("Premium");
        assertThat(plans.get(2).get("price").asDouble()).isEqualTo(499.0);
    }

    @Test
    void listPlans_eachPlanHasIdNamePriceDescription() throws Exception {
        registerUser("planlist2@example.com");
        String token = loginAndGetToken("planlist2@example.com");

        mockMvc.perform(get("/api/users/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(freePlanId.toString()))
                .andExpect(jsonPath("$[0].name").value("Free"))
                .andExpect(jsonPath("$[0].price").value(0))
                .andExpect(jsonPath("$[0].description").value("Basic access"));
    }

    @Test
    void listPlans_doesNotExposeTimestamps() throws Exception {
        registerUser("planlist3@example.com");
        String token = loginAndGetToken("planlist3@example.com");

        mockMvc.perform(get("/api/users/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    void listPlans_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/plans"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PATCH /api/users/me/plan ====================

    @Test
    void changePlan_freeToPro_returnsUpdatedUserAndNewToken() throws Exception {
        registerUser("changep1@example.com");
        String token = loginAndGetToken("changep1@example.com");

        mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.user.planId").value(proPlanId.toString()))
                .andExpect(jsonPath("$.user.planName").value("Pro"));
    }

    @Test
    void changePlan_newTokenContainsUpdatedPlanClaim() throws Exception {
        registerUser("changep2@example.com");
        String token = loginAndGetToken("changep2@example.com");

        String body = mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + premiumPlanId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newToken = objectMapper.readTree(body).get("accessToken").asText();
        assertThat(newToken).isNotEqualTo(token);

        // Use the new token to call /api/users/me — the plan should be updated.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Premium"));
    }

    @Test
    void changePlan_unknownPlan_returns404() throws Exception {
        registerUser("changep3@example.com");
        String token = loginAndGetToken("changep3@example.com");
        UUID unknownPlanId = UUID.randomUUID();

        mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + unknownPlanId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLAN_NOT_FOUND"));
    }

    @Test
    void changePlan_missingPlanId_returns400() throws Exception {
        registerUser("changep4@example.com");
        String token = loginAndGetToken("changep4@example.com");

        mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void changePlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/me/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePlan_persistedChange_survivesSubsequentRead() throws Exception {
        registerUser("changep5@example.com");
        String token = loginAndGetToken("changep5@example.com");

        String body = mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + proPlanId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newToken = objectMapper.readTree(body).get("accessToken").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(proPlanId.toString()))
                .andExpect(jsonPath("$.planName").value("Pro"));
    }

    @Test
    void changePlan_toSamePlan_succeeds() throws Exception {
        registerUser("changep6@example.com");
        String token = loginAndGetToken("changep6@example.com");

        mockMvc.perform(patch("/api/users/me/plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + freePlanId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.planName").value("Free"));
    }

    // ==================== Regression ====================

    @Test
    void getUsersMe_stillWorks_afterPlanEndpointsExist() throws Exception {
        registerUser("regression-plan@example.com");
        String token = loginAndGetToken("regression-plan@example.com");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("regression-plan@example.com"));
    }
}
