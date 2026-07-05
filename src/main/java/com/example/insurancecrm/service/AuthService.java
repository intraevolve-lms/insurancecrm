package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.ChangePasswordRequest;
import com.example.insurancecrm.dto.request.LoginRequest;
import com.example.insurancecrm.dto.response.AuthResponse;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

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

        AuthResponse response = buildAuthResponse(user);
        response.setRefreshToken(refreshToken);
        return response;
    }

    /** Self-service password change — requires the current password, also clears mustChangePassword. */
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

    private AuthResponse buildAuthResponse(User user) {
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
