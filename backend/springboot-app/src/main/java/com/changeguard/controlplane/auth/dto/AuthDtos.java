package com.changeguard.controlplane.auth.dto;

import com.changeguard.controlplane.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Authentication DTOs (docs/API_DESIGN.md §5.1 + Phase 1 registration).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            UserRole role,
            boolean enabled) {
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInMinutes,
            UserResponse user) {

        public static AuthResponse bearer(String token, long expiresInMinutes, UserResponse user) {
            return new AuthResponse(token, "Bearer", expiresInMinutes, user);
        }
    }

    public record MeResponse(UUID id, String email, String displayName, UserRole role) {
        public static MeResponse from(UserResponse user) {
            return new MeResponse(user.id(), user.email(), user.displayName(), user.role());
        }
    }

    public record LogoutResponse(@NotNull Boolean accepted) {
    }
}
