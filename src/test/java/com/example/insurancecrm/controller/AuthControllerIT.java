package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test against a real (dedicated test) MongoDB instance — see
 * src/test/resources/application.yaml for the isolated database name, so this never touches
 * the developer's working "test" database.
 *
 * MockMvc is built manually (rather than via @AutoConfigureMockMvc) because this Spring Boot
 * version doesn't bundle spring-boot-test-autoconfigure's web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private static final String EMAIL = "authit-agent@test.com";
    private static final String PASSWORD = "Password@123";
    private static final String ADMIN_EMAIL = "authit-admin@test.com";
    private static final String ADMIN_PASSWORD = "Password@123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        userRepository.save(User.builder()
                .name("Auth IT Agent").email(EMAIL).password(passwordEncoder.encode(PASSWORD))
                .role(Role.AGENT).active(true).createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder()
                .name("Auth IT Admin").email(ADMIN_EMAIL).password(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.ADMIN).active(true).createdAt(LocalDateTime.now()).build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    @Test
    void login_correctCredentials_returnsTokensAndUserInfo() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("AGENT"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_nonExistentEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost-" + EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_deactivatedAccount_isRejectedWithAuthenticationError_notA500() throws Exception {
        // Regression test: DisabledException previously fell through GlobalExceptionHandler's
        // narrow BadCredentialsException-only handler to the generic 500 handler.
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_missingPassword_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_validRefreshToken_issuesNewAccessToken() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).get("data").get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void refresh_garbageToken_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "not-a-real-token"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refresh_usingAccessTokenInsteadOfRefreshToken_isRejected() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).get("data").get("token").asText();

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", accessToken))))
                .andExpect(status().isForbidden());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("data").get("token").asText();
    }

    @Test
    void changePassword_noAuthToken_returns401() throws Exception {
        // Regression test: /api/auth/** used to be entirely permitAll, which would have left
        // this endpoint wide open to anyone with just an email.
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD, "newPassword", "NewPassword@456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_agentRole_isForbidden() throws Exception {
        // Agents cannot self-service change their password — only an admin can set it for them
        // (via PUT /api/users/{id}).
        String accessToken = loginAndGetAccessToken(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD, "newPassword", "NewPassword@456"))))
                .andExpect(status().isForbidden());

        // Old password still works — nothing changed.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_correctCurrentPassword_updatesPasswordAndOldPasswordStopsWorking() throws Exception {
        String accessToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ADMIN_PASSWORD, "newPassword", "NewPassword@456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, "NewPassword@456")))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400AndLeavesPasswordUnchanged() throws Exception {
        String accessToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrong-password", "newPassword", "NewPassword@456"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_newPasswordTooShort_returns400ValidationError() throws Exception {
        String accessToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ADMIN_PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest());
    }
}
