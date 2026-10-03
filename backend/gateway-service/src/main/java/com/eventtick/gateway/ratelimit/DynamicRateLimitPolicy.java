package com.eventtick.gateway.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One persisted rate-limit policy, stored as a Redis Hash entry. The
 * category:tier combination is the logical unique key; {@code id} is a
 * surrogate UUID for REST addressability (PUT/DELETE by id).
 */
public class DynamicRateLimitPolicy {

    private String id;
    private String category;
    private String tier;
    private int replenishRate;
    private int burstCapacity;
    private int requestedTokens;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public DynamicRateLimitPolicy() {
    }

    public DynamicRateLimitPolicy(String category, String tier, int replenishRate, int burstCapacity,
                                   int requestedTokens, boolean enabled) {
        this.id = UUID.randomUUID().toString();
        this.category = category;
        this.tier = tier;
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;
        this.requestedTokens = requestedTokens;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String policyKey() {
        return category + ":" + tier;
    }

    Map<String, String> toHash() {
        return Map.of(
                "id", id,
                "category", category,
                "tier", tier,
                "replenishRate", String.valueOf(replenishRate),
                "burstCapacity", String.valueOf(burstCapacity),
                "requestedTokens", String.valueOf(requestedTokens),
                "enabled", String.valueOf(enabled),
                "createdAt", createdAt.toString(),
                "updatedAt", updatedAt.toString());
    }

    static DynamicRateLimitPolicy fromHash(Map<String, String> hash) {
        DynamicRateLimitPolicy p = new DynamicRateLimitPolicy();
        p.id = hash.get("id");
        p.category = hash.get("category");
        p.tier = hash.get("tier");
        p.replenishRate = Integer.parseInt(hash.get("replenishRate"));
        p.burstCapacity = Integer.parseInt(hash.get("burstCapacity"));
        p.requestedTokens = Integer.parseInt(hash.getOrDefault("requestedTokens", "1"));
        p.enabled = Boolean.parseBoolean(hash.get("enabled"));
        p.createdAt = Instant.parse(hash.get("createdAt"));
        p.updatedAt = Instant.parse(hash.get("updatedAt"));
        return p;
    }

    // --- getters/setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public int getReplenishRate() { return replenishRate; }
    public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
    public int getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
    public int getRequestedTokens() { return requestedTokens; }
    public void setRequestedTokens(int requestedTokens) { this.requestedTokens = requestedTokens; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
