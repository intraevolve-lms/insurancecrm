package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CommunicationLogRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the owner-or-admin object-level authorization on CommunicationLogController —
 * getByCustomer/logForCustomer previously had none at all, letting any authenticated agent view
 * or log activity against any customer regardless of assignment.
 * MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CommunicationLogControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CommunicationLogRepository logRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "cl-admin@test.com";
    private static final String OWNER_EMAIL = "cl-owner@test.com";
    private static final String OTHER_EMAIL = "cl-other@test.com";

    private String adminToken;
    private String ownerToken;
    private String otherToken;
    private String customerId;
    private String ownerLogId;
    private String otherLogId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();

        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        User owner = userRepository.save(User.builder().name("Owner").email(OWNER_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());
        User other = userRepository.save(User.builder().name("Other").email(OTHER_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        ownerToken = jwtUtil.generateAccessToken(owner.getEmail(), owner.getId(), "AGENT");
        otherToken = jwtUtil.generateAccessToken(other.getEmail(), other.getId(), "AGENT");

        Customer customer = customerRepository.save(Customer.builder()
                .name("CL Access Test Customer").phone("9000000077").assignedAgentId(owner.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        customerId = customer.getId();

        ownerLogId = logRepository.save(CommunicationLog.builder()
                .customerId(customerId).channel(CommunicationChannel.CALL)
                .outcome(CommunicationOutcome.CALLBACK).loggedBy(owner.getId())
                .loggedByName(owner.getName()).loggedAt(LocalDateTime.now()).build()).getId();
        otherLogId = logRepository.save(CommunicationLog.builder()
                .customerId(customerId).channel(CommunicationChannel.CALL)
                .outcome(CommunicationOutcome.PROSPECT).loggedBy(other.getId())
                .loggedByName(other.getName()).loggedAt(LocalDateTime.now()).build()).getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OWNER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_EMAIL).ifPresent(userRepository::delete);
        if (customerId != null) {
            logRepository.findByCustomerIdOrderByLoggedAtDesc(customerId).forEach(logRepository::delete);
            customerRepository.findById(customerId).ifPresent(customerRepository::delete);
        }
    }

    private String logRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of("channel", "CALL", "outcome", "RINGING"));
    }

    // ── getByCustomer ──────────────────────────────────────────────

    @Test
    void getByCustomer_owningAgent_isAllowed() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/communications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getByCustomer_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/communications")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByCustomer_admin_isAllowed() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/communications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── logForCustomer ─────────────────────────────────────────────

    @Test
    void logForCustomer_owningAgent_isAllowed() throws Exception {
        mockMvc.perform(post("/api/customers/" + customerId + "/communications")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequestJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void logForCustomer_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/customers/" + customerId + "/communications")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequestJson()))
                .andExpect(status().isForbidden());
    }

    // ── unauthenticated ─────────────────────────────────────────────

    @Test
    void getByCustomer_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/communications"))
                .andExpect(status().isUnauthorized());
    }

    // ── delete: owner-or-admin (independent of customer assignment) ───

    @Test
    void delete_ownLog_agent_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/communications/" + ownerLogId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void delete_anotherAgentsLog_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/communications/" + otherLogId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_anyAgentsLog_admin_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/communications/" + otherLogId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void delete_noToken_returns401NotForbidden() throws Exception {
        mockMvc.perform(delete("/api/communications/" + ownerLogId))
                .andExpect(status().isUnauthorized());
    }
}
