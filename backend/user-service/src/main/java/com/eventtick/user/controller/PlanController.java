package com.eventtick.user.controller;

import com.eventtick.user.dto.ChangePlanRequest;
import com.eventtick.user.dto.PlanChangeResponse;
import com.eventtick.user.dto.PlanResponse;
import com.eventtick.user.dto.UserResponse;
import com.eventtick.user.entity.User;
import com.eventtick.user.security.JwtService;
import com.eventtick.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class PlanController {

    private final UserService userService;
    private final JwtService jwtService;

    public PlanController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @GetMapping("/plans")
    public List<PlanResponse> listPlans() {
        return userService.listPlans().stream().map(PlanResponse::from).toList();
    }

    @PatchMapping("/me/plan")
    public PlanChangeResponse changePlan(@Valid @RequestBody ChangePlanRequest request,
                                         Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = userService.changePlan(userId, request.planId());
        String token = jwtService.generateToken(user);
        return PlanChangeResponse.of(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }
}
