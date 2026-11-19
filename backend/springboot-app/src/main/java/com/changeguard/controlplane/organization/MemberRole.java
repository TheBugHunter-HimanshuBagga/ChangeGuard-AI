package com.changeguard.controlplane.organization;

/**
 * Organization-scoped membership role (Phase 1, docs/DATABASE_DESIGN.md §3.2a).
 */
public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER
}
