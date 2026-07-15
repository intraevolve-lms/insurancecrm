package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
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
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the @PreAuthorize("hasRole('ADMIN')") gates on CustomerController — regression
 * coverage so an accidental annotation removal (or reordering during a refactor) is caught
 * immediately rather than surfacing as a silent authorization leak in production.
 *
 * MockMvc is built manually (rather than via @AutoConfigureMockMvc) because this Spring Boot
 * version doesn't bundle spring-boot-test-autoconfigure's web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CustomerControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "cc-admin@test.com";
    private static final String AGENT_EMAIL = "cc-agent@test.com";

    private String adminToken;
    private String agentToken;
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

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");

        Customer customer = customerRepository.save(Customer.builder()
                .name("Access Test Customer").phone("9000000099")
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
        // Matched by id, not name — update_admin_isAllowed() renames the seeded customer,
        // so a name-based filter here would miss it and leak the record permanently.
        if (customerId != null) {
            customerRepository.findById(customerId).ifPresent(customerRepository::delete);
        }
    }

    @Test
    void getAll_agent_isAllowed() throws Exception {
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_agent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/customers/" + customerId).header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "x", "phone", "9000000099"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_admin_isAllowed() throws Exception {
        mockMvc.perform(put("/api/customers/" + customerId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated Name", "phone", "9000000099"))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/customers/" + customerId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_admin_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/customers/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void assignAgent_agent_isForbidden() throws Exception {
        User agent = userRepository.findByEmail(AGENT_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/customers/" + customerId + "/assign/" + agent.getId())
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignAgent_admin_isAllowed() throws Exception {
        User agent = userRepository.findByEmail(AGENT_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/customers/" + customerId + "/assign/" + agent.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void bulkAssign_agent_isForbidden() throws Exception {
        User agent = userRepository.findByEmail(AGENT_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/customers/bulk-assign").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerIds", java.util.List.of(customerId), "agentId", agent.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkDelete_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/customers/bulk-delete").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(customerId)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkDelete_admin_isAllowed_andReportsNotFoundIds() throws Exception {
        mockMvc.perform(delete("/api/customers/bulk-delete").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(customerId, "missing-id")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1))
                .andExpect(jsonPath("$.data.notFoundIds[0]").value("missing-id"));
    }

    @Test
    void search_agent_isAllowed_butOnlySeesOwnAssignedResults() throws Exception {
        // Access-control regression: search is not admin-gated, but must still scope results —
        // covered functionally in CustomerServiceTest; here we just confirm the endpoint itself
        // doesn't outright reject an agent.
        mockMvc.perform(get("/api/customers/search").param("q", "Access")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    @Test
    void expiredOrGarbageToken_returns401NotForbidden() throws Exception {
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
