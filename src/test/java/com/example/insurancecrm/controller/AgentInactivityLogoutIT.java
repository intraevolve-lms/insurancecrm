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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers auto-logout of idle AGENT sessions — configured via
 *  app.security.agent-inactivity-timeout-minutes (30 in application.yaml). Admins are never
 *  subject to this timeout, matching the same AGENT-only scoping as the force-logout feature. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AgentInactivityLogoutIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String AGENT_EMAIL = "idle-agent@test.com";
    private static final String ADMIN_EMAIL = "idle-admin@test.com";
    private static final String PASSWORD = "Password@123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        userRepository.save(User.builder().name("Idle Agent").email(AGENT_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());
        userRepository.save(User.builder().name("Idle Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("token").asText();
    }

    private String loginAndGetRefreshToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("refreshToken").asText();
    }

    private ResultActions probe(String accessToken) throws Exception {
        // GET /api/customers only needs a valid authenticated principal, any role — good for
        // proving whether a token is still accepted at all.
        return mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + accessToken));
    }

    private void markLastActivity(String email, LocalDateTime lastActivityAt) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setLastActivityAt(lastActivityAt);
        userRepository.save(user);
    }

    @Test
    void login_setsLastActivityAtImmediately() throws Exception {
        login(AGENT_EMAIL);

        User user = userRepository.findByEmail(AGENT_EMAIL).orElseThrow();
        assertThat(user.getLastActivityAt()).isNotNull();
    }

    @Test
    void agentIdleBeyondTimeout_accessTokenIsRejected() throws Exception {
        String accessToken = login(AGENT_EMAIL);
        markLastActivity(AGENT_EMAIL, LocalDateTime.now().minusMinutes(31));

        probe(accessToken).andExpect(status().isUnauthorized());
    }

    @Test
    void agentWithinTimeout_accessTokenStillWorksAndActivityIsRefreshed() throws Exception {
        String accessToken = login(AGENT_EMAIL);
        markLastActivity(AGENT_EMAIL, LocalDateTime.now().minusMinutes(29));

        probe(accessToken).andExpect(status().isOk());

        User user = userRepository.findByEmail(AGENT_EMAIL).orElseThrow();
        assertThat(user.getLastActivityAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void agentIdleBeyondTimeout_refreshTokenIsRejectedWithInactivityMessage() throws Exception {
        String refreshToken = loginAndGetRefreshToken(AGENT_EMAIL);
        markLastActivity(AGENT_EMAIL, LocalDateTime.now().minusMinutes(31));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your session expired due to inactivity — please log in again"));
    }

    @Test
    void adminIdleBeyondAgentTimeout_isNeverLoggedOut() throws Exception {
        String accessToken = login(ADMIN_EMAIL);
        markLastActivity(ADMIN_EMAIL, LocalDateTime.now().minusMinutes(31));

        probe(accessToken).andExpect(status().isOk());
    }
}
