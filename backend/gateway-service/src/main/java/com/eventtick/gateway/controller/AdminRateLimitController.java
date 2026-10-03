package com.eventtick.gateway.controller;

import com.eventtick.gateway.ratelimit.DynamicPolicyService;
import com.eventtick.gateway.ratelimit.DynamicRateLimitPolicy;
import com.eventtick.gateway.ratelimit.RateLimitActivityRecorder;
import com.eventtick.gateway.ratelimit.RateLimitPolicyProperties;
import com.eventtick.gateway.ratelimit.dto.CreatePolicyRequest;
import com.eventtick.gateway.ratelimit.dto.PolicyResponse;
import com.eventtick.gateway.ratelimit.dto.RateLimitPolicyDto;
import com.eventtick.gateway.ratelimit.dto.RateLimitStatsResponse;
import com.eventtick.gateway.ratelimit.dto.UpdatePolicyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 19: admin CRUD for dynamic rate-limit policies, plus the existing
 * FR-40 stats endpoint. All endpoints are under {@code /api/admin/**} and
 * therefore require {@code ROLE_ADMIN} via {@code GatewaySecurityConfig}.
 */
@RestController
public class AdminRateLimitController {

    private static final String FALLBACK_POLICY_ID = "FALLBACK";

    private final DynamicPolicyService policyService;
    private final RateLimitPolicyProperties staticProperties;
    private final RateLimitActivityRecorder activityRecorder;

    public AdminRateLimitController(DynamicPolicyService policyService,
                                     RateLimitPolicyProperties staticProperties,
                                     RateLimitActivityRecorder activityRecorder) {
        this.policyService = policyService;
        this.staticProperties = staticProperties;
        this.activityRecorder = activityRecorder;
    }

    // --- Policy CRUD ---

    @GetMapping("/api/admin/rate-limits/policies")
    public Mono<List<PolicyResponse>> listPolicies() {
        return policyService.findAll()
                .map(PolicyResponse::from)
                .sort(Comparator.comparing(PolicyResponse::category).thenComparing(PolicyResponse::tier))
                .collectList();
    }

    @PostMapping("/api/admin/rate-limits/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PolicyResponse> createPolicy(@RequestBody CreatePolicyRequest request) {
        validateCategory(request.category());
        validateTier(request.tier());
        validateRates(request.replenishRate(), request.burstCapacity(), request.requestedTokens());

        return policyService.create(request.category(), request.tier(),
                        request.replenishRate(), request.burstCapacity(),
                        request.requestedTokens() > 0 ? request.requestedTokens() : 1)
                .map(PolicyResponse::from)
                .onErrorResume(DynamicPolicyService.DuplicatePolicyException.class,
                        e -> Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage())));
    }

    @PutMapping("/api/admin/rate-limits/policies/{id}")
    public Mono<PolicyResponse> updatePolicy(@PathVariable String id, @RequestBody UpdatePolicyRequest request) {
        validateRates(request.replenishRate(), request.burstCapacity(), request.requestedTokens());

        return policyService.update(id, request.replenishRate(), request.burstCapacity(),
                        request.requestedTokens() > 0 ? request.requestedTokens() : 1, request.enabled())
                .map(PolicyResponse::from)
                .onErrorResume(DynamicPolicyService.PolicyNotFoundException.class,
                        e -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage())));
    }

    @DeleteMapping("/api/admin/rate-limits/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePolicy(@PathVariable String id) {
        return policyService.delete(id)
                .onErrorResume(DynamicPolicyService.PolicyNotFoundException.class,
                        e -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage())));
    }

    // --- Stats (FR-40, updated for dynamic policies) ---

    @GetMapping("/api/admin/rate-limits/stats")
    public Mono<RateLimitStatsResponse> stats() {
        Map<String, Map<String, RateLimitPolicyDto>> policies = new LinkedHashMap<>();
        List<String> policyIds = new ArrayList<>();

        Map<String, DynamicRateLimitPolicy> snapshot = policyService.getCacheSnapshot();
        snapshot.values().stream()
                .sorted(Comparator.comparing(DynamicRateLimitPolicy::getCategory)
                        .thenComparing(DynamicRateLimitPolicy::getTier))
                .forEach(p -> {
                    policies.computeIfAbsent(p.getCategory(), k -> new LinkedHashMap<>())
                            .put(p.getTier(), new RateLimitPolicyDto(p.getReplenishRate(), p.getBurstCapacity(), p.getRequestedTokens()));
                    if (p.isEnabled()) {
                        policyIds.add(p.policyKey());
                    }
                });
        policyIds.add(FALLBACK_POLICY_ID);

        RateLimitPolicyDto fallback = new RateLimitPolicyDto(
                staticProperties.getFallback().getReplenishRate(),
                staticProperties.getFallback().getBurstCapacity(),
                staticProperties.getFallback().getRequestedTokens());

        return activityRecorder.readActivitySection(policyIds)
                .map(activity -> new RateLimitStatsResponse(policies, fallback, activity));
    }

    // --- Validation ---

    private void validateCategory(String category) {
        if (category == null || !DynamicPolicyService.VALID_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid category: " + category + ". Valid: " + DynamicPolicyService.VALID_CATEGORIES);
        }
    }

    private void validateTier(String tier) {
        if (tier == null || !DynamicPolicyService.VALID_TIERS.contains(tier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid tier: " + tier + ". Valid: " + DynamicPolicyService.VALID_TIERS);
        }
    }

    private void validateRates(int replenishRate, int burstCapacity, int requestedTokens) {
        if (replenishRate < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "replenishRate must be >= 1");
        }
        if (burstCapacity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "burstCapacity must be >= 1");
        }
        if (requestedTokens < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestedTokens must be >= 0");
        }
    }
}
