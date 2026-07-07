package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.ChangePasswordRequest;
import com.example.insurancecrm.dto.request.LoginRequest;
import com.example.insurancecrm.dto.response.AuthResponse;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.agent-inactivity-timeout-minutes}")
    private long agentInactivityTimeoutMinutes;

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        return buildAuthResponse(user);
    }

    /** Exchanges a valid, non-expired refresh token for a new access token. */
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw ApiException.forbidden("Invalid or expired refresh token — please log in again");
        }

        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated");
        }

        if (jwtUtil.isIssuedBefore(refreshToken, user.getTokensInvalidBefore())) {
            throw ApiException.forbidden("Your session was ended by an administrator — please log in again");
        }

        if (isAgentInactive(user)) {
            throw ApiException.forbidden("Your session expired due to inactivity — please log in again");
        }

        AuthResponse response = buildAuthResponse(user);
        response.setRefreshToken(refreshToken);
        return response;
    }

    /** Self-service password change, admin-only (enforced by @PreAuthorize on the controller) —
     *  requires the current password, also clears mustChangePassword. */
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    /** True if this is an agent (never an admin) whose last recorded activity is older than the configured timeout. */
    private boolean isAgentInactive(User user) {
        return user.getRole() == Role.AGENT
                && user.getLastActivityAt() != null
                && Duration.between(user.getLastActivityAt(), LocalDateTime.now()).toMinutes() >= agentInactivityTimeoutMinutes;
    }

    /** Login and refresh both count as activity, resetting the agent inactivity clock. */
    private AuthResponse buildAuthResponse(User user) {
        user.setLastActivityAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId(), user.getRole().name());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }
}
