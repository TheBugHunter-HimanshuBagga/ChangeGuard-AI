package com.changeguard.controlplane.user;

/**
 * Global application role (docs/DATABASE_DESIGN.md §3.1).
 * Organization-scoped roles live in {@code MemberRole}.
 */
public enum UserRole {
    ADMIN,
    MEMBER,
    VIEWER
}
