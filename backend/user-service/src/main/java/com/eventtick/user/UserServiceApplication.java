package com.eventtick.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Eventtick User Service.
 *
 * <p>Owns registration, authentication, user profiles, roles, and
 * subscription plans (the {@code users} and {@code plans} tables — see
 * {@code database/migrations/0001} and {@code 0002}). This is a bootable
 * skeleton only; no controllers, services, or repositories are implemented
 * yet.
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
