package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
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
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the admin "log out selected agents" feature: force-logout must invalidate an
 *  agent's already-issued access and refresh tokens immediately, without waiting for expiry. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserControllerForceLogoutIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "fl-admin@test.com";
    private static final String AGENT_EMAIL = "fl-agent@test.com";
    private static final String AGENT2_EMAIL = "fl-agent2@test.com";
    private static final String PASSWORD = "Password@123";

    private String adminToken;
    private String agentAccessToken;
    private String agentRefreshToken;
    private String agentId;
    private String adminId;
    private String agent2AccessToken;
    private String agent2Id;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        User agent = userRepository.save(User.builder().name("Agent").email(AGENT_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());
        User agent2 = userRepository.save(User.builder().name("Agent Two").email(AGENT2_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminId = admin.getId();
        agentId = agent.getId();
        agent2Id = agent2.getId();
        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentAccessToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");
        agentRefreshToken = jwtUtil.generateRefreshToken(agent.getEmail(), agent.getId(), "AGENT");
        agent2AccessToken = jwtUtil.generateAccessToken(agent2.getEmail(), agent2.getId(), "AGENT");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT2_EMAIL).ifPresent(userRepository::delete);
    }

    private org.springframework.test.web.servlet.ResultActions probeAgentAccessToken() throws Exception {
        // GET /api/customers only needs a valid authenticated principal, any role (unlike
        // /api/auth/change-password, which is admin-only) — good for proving whether a token
        // is still accepted at all, independent of what it's authorized to do.
        return mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + agentAccessToken));
    }

    private org.springframework.test.web.servlet.ResultActions probeAgent2AccessToken() throws Exception {
        return mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + agent2AccessToken));
    }

    @Test
    void forceLogout_revokesAgentAccessTokenImmediately() throws Exception {
        probeAgentAccessToken().andExpect(status().isOk());

        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(agentId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        probeAgentAccessToken().andExpect(status().isUnauthorized());
    }

    @Test
    void forceLogout_revokesAgentRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", agentRefreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(agentId)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", agentRefreshToken))))
                .andExpect(status().isForbidden());
    }

    @Test
    void forceLogout_adminIdsAreIgnored_adminOwnTokenKeepsWorking() throws Exception {
        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(adminId)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void forceLogout_emptyUserIds_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forceLogout_agentRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + agentAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(agentId)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void forceLogout_noAuthToken_returns401NotForbidden() throws Exception {
        mockMvc.perform(post("/api/users/force-logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(agentId)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forceLogout_multipleAgentsInOneCall_revokesBoth() throws Exception {
        probeAgentAccessToken().andExpect(status().isOk());
        probeAgent2AccessToken().andExpect(status().isOk());

        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(agentId, agent2Id)))))
                .andExpect(status().isOk());

        probeAgentAccessToken().andExpect(status().isUnauthorized());
        probeAgent2AccessToken().andExpect(status().isUnauthorized());
    }

    @Test
    void forceLogout_mixedAdminAndAgentIds_ignoresAdminButStillLogsOutTheAgent() throws Exception {
        probeAgentAccessToken().andExpect(status().isOk());

        mockMvc.perform(post("/api/users/force-logout").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(adminId, agentId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // The agent in the mixed list is still logged out...
        probeAgentAccessToken().andExpect(status().isUnauthorized());
        // ...while the admin who issued the call is unaffected.
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
