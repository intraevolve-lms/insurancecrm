package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CustomerRepository;
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
 * DashboardController had zero test coverage. The underlying scoping logic (admin sees all
 * customers, agent sees only their own) lives in DashboardService; this verifies the endpoint
 * itself is wired correctly and that scoping is actually applied end-to-end through the
 * Authentication object.
 * MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DashboardControllerIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "dash-admin@test.com";
    private static final String AGENT_EMAIL = "dash-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "dash-other-agent@test.com";

    private String adminToken;
    private String agentToken;
    private String ownCustomerId;
    private String otherCustomerId;

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
        User otherAgent = userRepository.save(User.builder().name("Other Agent").email(OTHER_AGENT_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");

        ownCustomerId = customerRepository.save(Customer.builder()
                .name("Dash Own Customer").phone("9000000091").assignedAgentId(agent.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()).getId();
        otherCustomerId = customerRepository.save(Customer.builder()
                .name("Dash Other Customer").phone("9000000092").assignedAgentId(otherAgent.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()).getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_AGENT_EMAIL).ifPresent(userRepository::delete);
        if (ownCustomerId != null) customerRepository.findById(ownCustomerId).ifPresent(customerRepository::delete);
        if (otherCustomerId != null) customerRepository.findById(otherCustomerId).ifPresent(customerRepository::delete);
    }

    @Test
    void getSummary_agent_seesOnlyOwnCustomerCount() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(1));
    }

    @Test
    void getSummary_admin_seesAllCustomersAcrossAgents() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(2));
    }

    @Test
    void getSummary_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
    }
}
