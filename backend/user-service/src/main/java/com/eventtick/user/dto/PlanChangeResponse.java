package com.eventtick.user.dto;

public record PlanChangeResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {

    public static PlanChangeResponse of(String accessToken, long expiresInSeconds, UserResponse user) {
        return new PlanChangeResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
