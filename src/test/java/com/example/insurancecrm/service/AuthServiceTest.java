package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.ChangePasswordRequest;
import com.example.insurancecrm.dto.request.LoginRequest;
import com.example.insurancecrm.dto.response.AuthResponse;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id("user-1").name("Agent One").email("agent@test.com")
                .password("encoded").role(Role.AGENT).active(true).build();
        ReflectionTestUtils.setField(authService, "agentInactivityTimeoutMinutes", 30L);
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private ChangePasswordRequest changePasswordRequest(String current, String newPass) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(newPass);
        return req;
    }

    @Test
    void login_validCredentials_returnsTokensAndUserInfo() {
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken("agent@test.com", "user-1", "AGENT")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("agent@test.com", "user-1", "AGENT")).thenReturn("refresh-token");

        AuthResponse response = authService.login(loginRequest("agent@test.com", "password"));

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getRole()).isEqualTo(Role.AGENT);
        assertThat(user.getLastActivityAt()).isNotNull();
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_userVanishesAfterAuthentication_throwsNotFound() {
        // Edge case: authentication succeeds against Spring Security's UserDetailsService,
        // but the user record can't be found again when building the response.
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("agent@test.com", "password")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refresh_validRefreshToken_issuesNewAccessTokenAndKeepsSameRefreshToken() {
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("agent@test.com");
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh-token-unused");

        AuthResponse response = authService.refresh("valid-refresh");

        assertThat(response.getToken()).isEqualTo("new-access-token");
        // The original refresh token is preserved, not rotated
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh");
    }

    @Test
    void refresh_accessTokenPassedInsteadOfRefreshToken_isRejected() {
        when(jwtUtil.isRefreshToken("actually-an-access-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("actually-an-access-token"))
                .isInstanceOf(ApiException.class);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void refresh_deactivatedUser_isRejected() {
        user.setActive(false);
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("agent@test.com");
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refresh_agentIdleBeyondTimeout_isRejected() {
        user.setLastActivityAt(LocalDateTime.now().minusMinutes(31));
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("agent@test.com");
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(ApiException.class);
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refresh_agentWithinTimeout_succeedsAndRefreshesLastActivity() {
        user.setLastActivityAt(LocalDateTime.now().minusMinutes(29));
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("agent@test.com");
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(any(), any(), any())).thenReturn("new-refresh-token-unused");

        authService.refresh("valid-refresh");

        assertThat(user.getLastActivityAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void refresh_userNoLongerExists_throwsNotFound() {
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("ghost@test.com");
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void changePassword_correctCurrentPassword_updatesPasswordAndClearsMustChangeFlag() {
        user.setMustChangePassword(true);
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass123", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("newPass456")).thenReturn("newly-encoded");

        authService.changePassword("agent@test.com", changePasswordRequest("oldPass123", "newPass456"));

        assertThat(user.getPassword()).isEqualTo("newly-encoded");
        assertThat(user.isMustChangePassword()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejectedWithoutSaving() {
        when(userRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("agent@test.com", changePasswordRequest("wrongPass", "newPass456")))
                .isInstanceOf(ApiException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_throwsNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("ghost@test.com", changePasswordRequest("any", "newPass456")))
                .isInstanceOf(ApiException.class);
    }
}
