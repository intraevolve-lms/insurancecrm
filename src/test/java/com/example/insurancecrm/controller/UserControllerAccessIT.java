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
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UserController is class-level @PreAuthorize("hasRole('ADMIN')") — every endpoint must reject agents.
 *  MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "uc-admin@test.com";
    private static final String AGENT_EMAIL = "uc-agent@test.com";

    private String adminToken;
    private String agentToken;
    private String agentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        User agent = userRepository.save(User.builder().name("Agent").email(AGENT_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");
        agentId = agent.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail("uc-created@test.com").ifPresent(userRepository::delete);
    }

    @Test
    void getAll_agent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAll_admin_isAllowed() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void create_agent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New User", "email", "uc-created@test.com", "password", "pw123", "role", "AGENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_admin_isAllowed() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New User", "email", "uc-created@test.com", "password", "pw123", "role", "AGENT"))))
                .andExpect(status().isCreated());
    }

    @Test
    void update_agent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/users/" + agentId).header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Renamed", "email", AGENT_EMAIL, "password", "", "role", "AGENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_admin_blankPassword_succeeds_regressionCoverageForThePreviouslyBrokenEditFlow() throws Exception {
        // This is exactly the payload the real frontend sends when an admin edits a user without
        // also setting a new password — previously rejected with 400 because the update endpoint
        // reused CreateUserRequest's @NotBlank password constraint. See UpdateUserRequest.
        mockMvc.perform(put("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Renamed Agent", "email", AGENT_EMAIL, "password", "", "role", "AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Agent"));
    }

    @Test
    void update_admin_omittedPassword_alsoSucceeds() throws Exception {
        mockMvc.perform(put("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Renamed Again", "email", AGENT_EMAIL, "role", "AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Again"));
    }

    @Test
    void update_admin_blankName_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "", "email", AGENT_EMAIL, "password", "", "role", "AGENT"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivate_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivate_admin_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void delete_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_admin_activeUser_isBadRequest() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_admin_deactivatedUser_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void delete_admin_neverFreesUpTheEmail_recordIsSoftDeletedNotRemoved() throws Exception {
        // The whole point of soft-delete: the email stays permanently claimed, since the document
        // (and its audit trail) is never physically removed from the database.
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Agent", "email", AGENT_EMAIL, "password", "pw123", "role", "AGENT"))))
                .andExpect(status().isConflict());

        User stillInDb = userRepository.findById(agentId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stillInDb.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_admin_deletedUserNoLongerAppearsInGetAll() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(agentId))));
    }

    @Test
    void delete_admin_deletingTwiceIsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + agentId + "/permanent").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_noToken_returns401NotForbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }
}
