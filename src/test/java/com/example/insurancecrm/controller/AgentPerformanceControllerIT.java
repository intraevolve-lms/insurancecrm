package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AgentPerformanceController had zero test coverage. Verifies the endpoint is wired correctly
 * and that an agent only ever sees their own row while an admin sees every active agent.
 * MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AgentPerformanceControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "perf-admin@test.com";
    private static final String AGENT_EMAIL = "perf-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "perf-other-agent@test.com";

    private String adminToken;
    private String agentToken;

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
        userRepository.save(User.builder().name("Other Agent").email(OTHER_AGENT_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_AGENT_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void getPerformance_agent_seesOnlyOwnRow() throws Exception {
        mockMvc.perform(get("/api/agent-performance").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].agentName").value("Agent"));
    }

    @Test
    void getPerformance_admin_seesEveryActiveAgent() throws Exception {
        mockMvc.perform(get("/api/agent-performance").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getPerformance_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/agent-performance")).andExpect(status().isUnauthorized());
    }
}
