package com.changeguard.controlplane.security;

import com.changeguard.controlplane.user.UserRole;
import java.util.UUID;

/**
 * Authenticated principal carried in the SecurityContext after JWT validation.
 */
public record AuthPrincipal(UUID id, String email, UserRole role) {
}
