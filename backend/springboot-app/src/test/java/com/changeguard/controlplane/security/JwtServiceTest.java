package com.changeguard.controlplane.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.changeguard.controlplane.user.User;
import com.changeguard.controlplane.user.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-32-plus-chars!";

    private JwtService newService() {
        return new JwtService(SECRET, 15);
    }

    @Test
    void generatesAndParsesTokenRoundTrip() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setRole(UserRole.MEMBER);

        String token = newService().generateToken(user);
        AuthPrincipal principal = newService().parseToken(token);

        assertThat(principal.id()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo("alice@example.com");
        assertThat(principal.role()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void rejectsTamperedToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bob@example.com");
        user.setDisplayName("Bob");
        user.setRole(UserRole.ADMIN);

        String token = newService().generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> newService().parseToken(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("carol@example.com");
        user.setDisplayName("Carol");
        user.setRole(UserRole.VIEWER);

        String token = newService().generateToken(user);
        JwtService other = new JwtService("another-secret-key-with-32-plus-chars!!", 15);

        assertThatThrownBy(() -> other.parseToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("dave@example.com");
        user.setDisplayName("Dave");
        user.setRole(UserRole.MEMBER);

        // ttlMinutes = 0 → token expires immediately (exp == iat)
        JwtService expiring = new JwtService(SECRET, 0);
        String token = expiring.generateToken(user);
        Thread.sleep(1100);

        assertThatThrownBy(() -> expiring.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void failsFastOnMissingSecret() {
        assertThatThrownBy(() -> new JwtService("", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    void failsFastOnShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }
}
