package com.eventtick.gateway.ratelimit;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Redis Hash-backed storage for dynamic rate-limit policies. Each policy
 * is stored as a nested Hash under {@value POLICIES_KEY}:{category}:{tier}.
 * The top-level {@value INDEX_KEY} Hash maps policy id → category:tier for
 * fast id-based lookups.
 */
@Component
class DynamicPolicyRepository {

    static final String POLICIES_KEY = "gateway:rate-limit-policies";
    static final String INDEX_KEY = "gateway:rate-limit-policies:index";

    private final ReactiveStringRedisTemplate redis;

    DynamicPolicyRepository(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    Mono<DynamicRateLimitPolicy> save(DynamicRateLimitPolicy policy) {
        String hashKey = POLICIES_KEY + ":" + policy.policyKey();
        return redis.opsForHash().putAll(hashKey, policy.toHash())
                .then(redis.opsForHash().put(INDEX_KEY, policy.getId(), policy.policyKey()))
                .thenReturn(policy);
    }

    Flux<DynamicRateLimitPolicy> findAll() {
        return redis.opsForHash().entries(INDEX_KEY)
                .flatMap(entry -> {
                    String policyKey = entry.getValue().toString();
                    return loadByPolicyKey(policyKey);
                });
    }

    Mono<DynamicRateLimitPolicy> findById(String id) {
        return redis.opsForHash().get(INDEX_KEY, id)
                .flatMap(policyKey -> loadByPolicyKey(policyKey.toString()));
    }

    Mono<DynamicRateLimitPolicy> findByCategoryAndTier(String category, String tier) {
        return loadByPolicyKey(category + ":" + tier);
    }

    Mono<Boolean> deleteById(String id) {
        return redis.opsForHash().get(INDEX_KEY, id)
                .flatMap(policyKey -> {
                    String hashKey = POLICIES_KEY + ":" + policyKey;
                    return redis.delete(hashKey)
                            .then(redis.opsForHash().remove(INDEX_KEY, id))
                            .thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    Mono<Long> count() {
        return redis.opsForHash().size(INDEX_KEY);
    }

    private Mono<DynamicRateLimitPolicy> loadByPolicyKey(String policyKey) {
        String hashKey = POLICIES_KEY + ":" + policyKey;
        return redis.opsForHash().entries(hashKey)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .filter(map -> !map.isEmpty())
                .map(DynamicRateLimitPolicy::fromHash);
    }
}
