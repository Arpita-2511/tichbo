package com.eventtick.payment.controller;

import com.eventtick.payment.TestTokens;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.security.PaymentRole;
import com.eventtick.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the <b>real</b> Spring Security filter chain
 * ({@code SecurityConfig} + {@code JwtAuthenticationFilter} +
 * {@code JwtService}), unlike {@link PaymentControllerTest} (which injects
 * an {@code Authentication} directly and never exercises the filter chain
 * itself). Mirrors user-service's own full-context security tests (e.g.
 * {@code CorsIntegrationTest}) in shape. {@link PaymentService} is still
 * mocked — this class is about authentication, not business logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken_is401() throws Exception {
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_is401() throws Exception {
        String expired = TestTokens.valid().expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();

        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSigningKey_is401() throws Exception {
        // A token that's structurally fine but wasn't signed with this
        // service's own configured secret.
        String foreign = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                .issuer(TestTokens.ISSUER)
                .audience().add(TestTokens.AUDIENCE).and()
                .expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "a-completely-different-test-only-secret-0987654321".getBytes()))
                .compact();

        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_reachesTheService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("key-1");

        when(paymentService.getPayment(paymentId)).thenReturn(payment);
        lenient().when(paymentService.canView(payment, userId, PaymentRole.CUSTOMER)).thenReturn(true);

        String token = TestTokens.valid().subject(userId).role("CUSTOMER").build();

        mockMvc.perform(get("/api/payments/" + paymentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminToken_isAcceptedTheSameWayCustomerTokenIs() throws Exception {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("key-1");

        when(paymentService.getPayment(paymentId)).thenReturn(payment);
        when(paymentService.canView(any(), any(), any())).thenReturn(true);

        String token = TestTokens.valid().role("ADMIN").build();

        mockMvc.perform(get("/api/payments/" + paymentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
