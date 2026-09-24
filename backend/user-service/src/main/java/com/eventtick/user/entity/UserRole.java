package com.eventtick.user.entity;

/**
 * Mirrors the {@code chk_users_role} CHECK constraint on the {@code users}
 * table (see {@code database/migrations/0002_create_users_table.up.sql}).
 */
public enum UserRole {
    CUSTOMER,
    ADMIN
}
