package com.changeguard.controlplane.auth;

import com.changeguard.controlplane.auth.dto.AuthDtos.AuthResponse;
import com.changeguard.controlplane.auth.dto.AuthDtos.LoginRequest;
import com.changeguard.controlplane.auth.dto.AuthDtos.LogoutResponse;
import com.changeguard.controlplane.auth.dto.AuthDtos.MeResponse;
import com.changeguard.controlplane.auth.dto.AuthDtos.RegisterRequest;
import com.changeguard.controlplane.auth.dto.AuthDtos.UserResponse;
import com.changeguard.controlplane.common.error.ApiException;
import com.changeguard.controlplane.common.error.ErrorCode;
import com.changeguard.controlplane.common.error.ErrorResponse;
import com.changeguard.controlplane.security.AuthPrincipal;
import com.changeguard.controlplane.security.JwtService;
import com.changeguard.controlplane.security.SecurityUtils;
import com.changeguard.controlplane.user.User;
import com.changeguard.controlplane.user.UserRepository;
import com.changeguard.controlplane.user.UserRole;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered",
                    List.of(new ErrorResponse.ErrorDetail("email", "Already registered")));
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.MEMBER);
        user.setEnabled(true);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered",
                    List.of(new ErrorResponse.ErrorDetail("email", "Already registered")));
        }
        log.info("User registered userId={} email={}", user.getId(), user.getEmail());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.USER_DISABLED, "Account is disabled");
        }
        log.info("User login userId={}", user.getId());
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthPrincipal principal = SecurityUtils.currentPrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        return MeResponse.from(toResponse(user));
    }

    /**
     * Stateless JWT: the token remains valid until expiry.
     * TODO(phase-later): add Redis-backed token revocation (DATABASE_DESIGN.md §4).
     */
    @Transactional(readOnly = true)
    public LogoutResponse logout() {
        SecurityUtils.currentPrincipal();
        return new LogoutResponse(true);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user);
        return AuthResponse.bearer(token, jwtService.getTtlMinutes(), toResponse(user));
    }

    /** Emails are stored lowercase; uniqueness also enforced by lower(email) index. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.isEnabled());
    }
}
