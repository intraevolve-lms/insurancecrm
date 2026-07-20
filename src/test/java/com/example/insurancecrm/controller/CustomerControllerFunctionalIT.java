package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.AuditLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
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

/**
 * Exercises the actual method bodies of CustomerController's create/getById/bulkAssignAgent
 * endpoints — CustomerControllerAccessIT only locks in the @PreAuthorize gates (many of its cases
 * never reach the controller body at all), so these three methods had zero functional coverage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CustomerControllerFunctionalIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "ccf-admin@test.com";
    private static final String AGENT_EMAIL = "ccf-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "ccf-other-agent@test.com";

    private String adminToken;
    private String agentToken;
    private String agentId;
    private String otherAgentId;
    private String customerId;

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
        agentId = agent.getId();
        otherAgentId = otherAgent.getId();

        Customer customer = customerRepository.save(Customer.builder()
                .name("Functional Test Customer").phone("9000000098")
                .assignedAgentId(agentId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        customerId = customer.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_AGENT_EMAIL).ifPresent(userRepository::delete);
        customerRepository.findAll().stream()
                .filter(c -> c.getPhone() != null && c.getPhone().startsWith("90000000"))
                .forEach(c -> {
                    auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("CUSTOMER", c.getId())
                            .forEach(auditLogRepository::delete);
                    customerRepository.delete(c);
                });
    }

    @Test
    void create_admin_returns201WithCreatedCustomer() throws Exception {
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Customer", "phone", "9000000097", "email", "new@test.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("New Customer"))
                .andExpect(jsonPath("$.data.email").value("new@test.com"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void create_agent_isAllowed_notAdminGated() throws Exception {
        // create has no @PreAuthorize — any authenticated user can create a customer.
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Agent Created", "phone", "9000000096"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Agent Created"));
    }

    @Test
    void create_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-name-or-phone@test.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_ownerAgent_returnsCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerId))
                .andExpect(jsonPath("$.data.name").value("Functional Test Customer"));
    }

    @Test
    void getById_admin_returnsCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerId));
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/customers/does-not-exist").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_recordsLastOpenedByOnSubsequentView() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastOpenedByName").value("Admin"));
    }

    @Test
    void bulkAssignAgent_admin_assignsAllAndReportsCounts() throws Exception {
        mockMvc.perform(patch("/api/customers/bulk-assign").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerIds", java.util.List.of(customerId), "agentId", otherAgentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(otherAgentId))
                .andExpect(jsonPath("$.data.requestedCount").value(1))
                .andExpect(jsonPath("$.data.assignedCount").value(1));

        mockMvc.perform(get("/api/customers/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedAgentId").value(otherAgentId));
    }

    @Test
    void bulkAssignAgent_unknownCustomerId_isReportedNotFound() throws Exception {
        mockMvc.perform(patch("/api/customers/bulk-assign").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerIds", java.util.List.of(customerId, "missing-id"), "agentId", otherAgentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.assignedCount").value(1))
                .andExpect(jsonPath("$.data.notFoundCustomerIds[0]").value("missing-id"));
    }

    @Test
    void bulkAssignAgent_emptyCustomerIds_returns400() throws Exception {
        mockMvc.perform(patch("/api/customers/bulk-assign").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerIds", java.util.List.of(), "agentId", otherAgentId))))
                .andExpect(status().isBadRequest());
    }
}
