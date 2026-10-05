package com.eventtick.user.dto;

import com.eventtick.user.entity.Plan;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanResponse(UUID id, String name, BigDecimal price, String description) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getPrice(), plan.getDescription());
    }
}
