package com.eventtick.gateway.ratelimit;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 19: manages dynamic rate-limit policies. Provides CRUD operations
 * that persist to Redis and immediately update the in-memory policy cache
 * and the {@link RedisRateLimiter}'s own config map, so policy changes
 * take effect without restarting the Gateway.
 *
 * <p><b>Consistency model</b>: Redis is the source of truth. The in-memory
 * {@code policyCache} is updated synchronously after every successful
 * Redis write. On startup, policies are loaded from Redis; if Redis has
 * none, the static defaults from {@code application.yml} are seeded.
 * A periodic refresh (every 30 seconds via {@code refreshCache}) guards
 * against cache drift in multi-instance deployments.
 *
 * <p><b>Failure behavior</b>: If a Redis write succeeds but the cache
 * update somehow fails (should not happen since it's in-memory), the
 * next periodic refresh picks up the correct state. If Redis is
 * unreachable, the existing in-memory cache continues to serve, matching
 * the gateway's existing fail-open pattern.
 */
@Service
public class DynamicPolicyService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPolicyService.class);

    public static final Set<String> VALID_CATEGORIES = Arrays.stream(RequestCategory.values())
            .filter(c -> c != RequestCategory.UNKNOWN)
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static final Set<String> VALID_TIERS = Arrays.stream(UserTier.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final DynamicPolicyRepository repository;
    private final RateLimitPolicyProperties staticProperties;
    private final RedisRateLimiter rateLimiter;

    private final ConcurrentHashMap<String, DynamicRateLimitPolicy> policyCache = new ConcurrentHashMap<>();

    DynamicPolicyService(DynamicPolicyRepository repository,
                          RateLimitPolicyProperties staticProperties,
                          RedisRateLimiter rateLimiter) {
        this.repository = repository;
        this.staticProperties = staticProperties;
        this.rateLimiter = rateLimiter;
    }

    @PostConstruct
    void initialize() {
        try {
            loadOrSeed().block();
        } catch (Exception e) {
            log.warn("Redis unavailable during startup — seeding in-memory cache from application.yml defaults. "
                    + "Dynamic policy CRUD will fail until Redis recovers. Error: {}", e.getMessage());
            seedCacheFromStaticProperties();
        }
    }

    public Mono<Void> loadOrSeed() {
        return repository.count()
                .flatMap(count -> {
                    if (count > 0) {
                        log.info("Loading {} dynamic rate-limit policies from Redis", count);
                        return loadAllIntoCache();
                    }
                    log.info("No dynamic policies in Redis — seeding from application.yml defaults");
                    return seedFromStaticProperties().then(loadAllIntoCache());
                });
    }

    private void seedCacheFromStaticProperties() {
        staticProperties.getPolicies().forEach((category, byTier) ->
                byTier.forEach((tier, values) -> {
                    DynamicRateLimitPolicy p = new DynamicRateLimitPolicy(
                            category.name(), tier.name(),
                            values.getReplenishRate(), values.getBurstCapacity(), values.getRequestedTokens(), true);
                    policyCache.put(p.policyKey(), p);
                    registerOnLimiter(p);
                }));
        log.info("Seeded {} policies into in-memory cache from application.yml", policyCache.size());
    }

    private Mono<Void> seedFromStaticProperties() {
        return Flux.fromIterable(staticProperties.getPolicies().entrySet())
                .flatMap(categoryEntry -> Flux.fromIterable(categoryEntry.getValue().entrySet())
                        .flatMap(tierEntry -> {
                            RateLimitPolicyProperties.PolicyValues v = tierEntry.getValue();
                            DynamicRateLimitPolicy p = new DynamicRateLimitPolicy(
                                    categoryEntry.getKey().name(), tierEntry.getKey().name(),
                                    v.getReplenishRate(), v.getBurstCapacity(), v.getRequestedTokens(), true);
                            return repository.save(p);
                        }))
                .then();
    }

    private Mono<Void> loadAllIntoCache() {
        return repository.findAll()
                .doOnNext(p -> {
                    policyCache.put(p.policyKey(), p);
                    registerOnLimiter(p);
                })
                .then();
    }

    @Scheduled(fixedDelay = 30000)
    public void refreshCache() {
        try {
            ConcurrentHashMap<String, DynamicRateLimitPolicy> fresh = new ConcurrentHashMap<>();
            repository.findAll()
                    .doOnNext(p -> {
                        fresh.put(p.policyKey(), p);
                        registerOnLimiter(p);
                    })
                    .then(Mono.fromRunnable(() -> {
                        policyCache.clear();
                        policyCache.putAll(fresh);
                    }))
                    .block();
        } catch (Exception e) {
            log.warn("Periodic cache refresh failed (Redis unavailable?) — keeping existing in-memory cache. Error: {}", e.getMessage());
        }
    }

    // --- CRUD ---

    public Flux<DynamicRateLimitPolicy> findAll() {
        return Flux.fromIterable(policyCache.values());
    }

    public Mono<DynamicRateLimitPolicy> findById(String id) {
        return Mono.justOrEmpty(policyCache.values().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst());
    }

    public Mono<DynamicRateLimitPolicy> create(String category, String tier,
                                                 int replenishRate, int burstCapacity, int requestedTokens) {
        String policyKey = category + ":" + tier;
        if (policyCache.containsKey(policyKey)) {
            return Mono.error(new DuplicatePolicyException(category, tier));
        }
        DynamicRateLimitPolicy policy = new DynamicRateLimitPolicy(
                category, tier, replenishRate, burstCapacity, requestedTokens, true);
        return repository.save(policy)
                .doOnNext(p -> {
                    policyCache.put(p.policyKey(), p);
                    registerOnLimiter(p);
                    log.info("Created rate-limit policy: {} rate={} burst={}", p.policyKey(), p.getReplenishRate(), p.getBurstCapacity());
                });
    }

    public Mono<DynamicRateLimitPolicy> update(String id, int replenishRate, int burstCapacity,
                                                 int requestedTokens, boolean enabled) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new PolicyNotFoundException(id)))
                .flatMap(existing -> {
                    existing.setReplenishRate(replenishRate);
                    existing.setBurstCapacity(burstCapacity);
                    existing.setRequestedTokens(requestedTokens);
                    existing.setEnabled(enabled);
                    existing.setUpdatedAt(Instant.now());
                    return repository.save(existing)
                            .doOnNext(p -> {
                                policyCache.put(p.policyKey(), p);
                                registerOnLimiter(p);
                                log.info("Updated rate-limit policy: {} rate={} burst={} enabled={}",
                                        p.policyKey(), p.getReplenishRate(), p.getBurstCapacity(), p.isEnabled());
                            });
                });
    }

    public Mono<Void> delete(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new PolicyNotFoundException(id)))
                .flatMap(existing -> repository.deleteById(id)
                        .doOnNext(deleted -> {
                            policyCache.remove(existing.policyKey());
                            rateLimiter.getConfig().remove(existing.policyKey());
                            log.info("Deleted rate-limit policy: {}", existing.policyKey());
                        })
                        .then());
    }

    // --- Cache lookup used by the resolver ---

    RateLimitPolicyProperties.PolicyValues lookupPolicy(RequestCategory category, UserTier tier) {
        DynamicRateLimitPolicy p = policyCache.get(category.name() + ":" + tier.name());
        if (p == null || !p.isEnabled()) {
            return null;
        }
        RateLimitPolicyProperties.PolicyValues values = new RateLimitPolicyProperties.PolicyValues();
        values.setReplenishRate(p.getReplenishRate());
        values.setBurstCapacity(p.getBurstCapacity());
        values.setRequestedTokens(p.getRequestedTokens());
        return values;
    }

    public Map<String, DynamicRateLimitPolicy> getCacheSnapshot() {
        return Map.copyOf(policyCache);
    }

    // --- Register on the RedisRateLimiter ---

    private void registerOnLimiter(DynamicRateLimitPolicy p) {
        if (p.isEnabled()) {
            rateLimiter.getConfig().put(p.policyKey(), new RedisRateLimiter.Config()
                    .setReplenishRate(p.getReplenishRate())
                    .setBurstCapacity(p.getBurstCapacity())
                    .setRequestedTokens(p.getRequestedTokens()));
        } else {
            rateLimiter.getConfig().remove(p.policyKey());
        }
    }

    // --- Exceptions ---

    public static class DuplicatePolicyException extends RuntimeException {
        public DuplicatePolicyException(String category, String tier) {
            super("A policy for " + category + ":" + tier + " already exists");
        }
    }

    public static class PolicyNotFoundException extends RuntimeException {
        public PolicyNotFoundException(String id) {
            super("Policy not found: " + id);
        }
    }
}
