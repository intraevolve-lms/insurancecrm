package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
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
 * getByCustomer/getByLead/logForCustomer/logForLead previously had none at all, letting any
 * authenticated agent view or log activity against any customer/lead regardless of assignment.
 * MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CommunicationLogControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LeadRepository leadRepository;
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
    private String leadId;

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

        Lead lead = leadRepository.save(Lead.builder()
                .name("CL Access Test Lead").phone("9000000078").source(LeadSource.REFERRAL)
                .status(LeadStatus.NEW).assignedAgentId(owner.getId())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        leadId = lead.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OWNER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_EMAIL).ifPresent(userRepository::delete);
        if (customerId != null) customerRepository.findById(customerId).ifPresent(customerRepository::delete);
        if (leadId != null) leadRepository.findById(leadId).ifPresent(leadRepository::delete);
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

    // ── getByLead / logForLead ─────────────────────────────────────

    @Test
    void getByLead_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/leads/" + leadId + "/communications")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByLead_owningAgent_isAllowed() throws Exception {
        mockMvc.perform(get("/api/leads/" + leadId + "/communications")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void logForLead_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/communications")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logForLead_admin_isAllowed() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/communications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequestJson()))
                .andExpect(status().isCreated());
    }

    // ── unauthenticated ─────────────────────────────────────────────

    @Test
    void getByCustomer_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/customers/" + customerId + "/communications"))
                .andExpect(status().isUnauthorized());
    }
}
