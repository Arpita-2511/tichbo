package com.eventtick.gateway.controller;

import com.eventtick.gateway.ratelimit.RateLimitActivityRecorder;
import com.eventtick.gateway.ratelimit.RateLimitPolicyProperties;
import com.eventtick.gateway.ratelimit.RequestCategory;
import com.eventtick.gateway.ratelimit.UserTier;
import com.eventtick.gateway.ratelimit.dto.RateLimitPolicyDto;
import com.eventtick.gateway.ratelimit.dto.RateLimitStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-40: {@code GET /api/admin/rate-limits/stats} — the first REST endpoint
 * gateway-service serves <b>locally</b> rather than proxying. Deliberately
 * has no corresponding {@code application.yml} route: this class answers
 * the request directly, so nothing is forwarded anywhere.
 *
 * <p><b>Authorization:</b> the existing {@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}
 * rule in {@code GatewaySecurityConfig} already applies here unchanged — it
 * is enforced by the reactive {@code SecurityWebFilterChain}, which runs
 * ahead of route/handler resolution regardless of whether a request is
 * ultimately served by a proxied route or, as here, a local
 * {@code @RestController}. No new security configuration is added.
 *
 * <p>Reuses existing state only: {@link RateLimitPolicyProperties} (already
 * loaded from {@code application.yml} at startup — nothing here is
 * hardcoded) and {@link RateLimitActivityRecorder} (FR-40's new, Redis-backed
 * counters). No new service and no admin-service.
 */
@RestController
public class AdminRateLimitController {

    /** Must match {@code RateLimitPolicyResolver.FALLBACK_POLICY_ID} (package-private; not modified for this endpoint). */
    private static final String FALLBACK_POLICY_ID = "FALLBACK";

    private final RateLimitPolicyProperties policyProperties;
    private final RateLimitActivityRecorder activityRecorder;

    public AdminRateLimitController(RateLimitPolicyProperties policyProperties,
                                     RateLimitActivityRecorder activityRecorder) {
        this.policyProperties = policyProperties;
        this.activityRecorder = activityRecorder;
    }

    @GetMapping("/api/admin/rate-limits/stats")
    public Mono<RateLimitStatsResponse> stats() {
        Map<String, Map<String, RateLimitPolicyDto>> policies = new LinkedHashMap<>();
        List<String> policyIds = new ArrayList<>();

        for (Map.Entry<RequestCategory, Map<UserTier, RateLimitPolicyProperties.PolicyValues>> byCategory
                : policyProperties.getPolicies().entrySet()) {
            Map<String, RateLimitPolicyDto> byTier = new LinkedHashMap<>();
            for (Map.Entry<UserTier, RateLimitPolicyProperties.PolicyValues> byTierEntry : byCategory.getValue().entrySet()) {
                byTier.put(byTierEntry.getKey().name(), toDto(byTierEntry.getValue()));
                policyIds.add(byCategory.getKey().name() + ":" + byTierEntry.getKey().name());
            }
            policies.put(byCategory.getKey().name(), byTier);
        }
        policyIds.add(FALLBACK_POLICY_ID);

        RateLimitPolicyDto fallback = toDto(policyProperties.getFallback());

        return activityRecorder.readActivitySection(policyIds)
                .map(activity -> new RateLimitStatsResponse(policies, fallback, activity));
    }

    private static RateLimitPolicyDto toDto(RateLimitPolicyProperties.PolicyValues values) {
        return new RateLimitPolicyDto(values.getReplenishRate(), values.getBurstCapacity(), values.getRequestedTokens());
    }
}
