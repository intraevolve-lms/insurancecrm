package com.example.insurancecrm.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the actual method bodies of LeadController's create/update/updateStatus/convert
 * endpoints. LeadControllerAccessIT only locks in the @PreAuthorize gates on delete/bulkDelete —
 * these four methods (none of which are admin-gated) had zero functional coverage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LeadControllerFunctionalIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "lcf-admin@test.com";
    private static final String AGENT_EMAIL = "lcf-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "lcf-other-agent@test.com";

    private String adminToken;
    private String agentToken;
    private String otherAgentToken;
    private String agentId;
    private String otherAgentId;
    private String leadId;

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
        otherAgentToken = jwtUtil.generateAccessToken(otherAgent.getEmail(), otherAgent.getId(), "AGENT");
        agentId = agent.getId();
        otherAgentId = otherAgent.getId();

        Lead lead = leadRepository.save(Lead.builder()
                .name("Functional Test Lead").phone("9100000098").source(LeadSource.REFERRAL)
                .status(LeadStatus.NEW).assignedAgentId(agentId).createdBy(agentId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        leadId = lead.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_AGENT_EMAIL).ifPresent(userRepository::delete);
        leadRepository.findAll().stream()
                .filter(l -> l.getPhone() != null && l.getPhone().startsWith("91000000"))
                .forEach(leadRepository::delete);
        customerRepository.findAll().stream()
                .filter(c -> c.getPhone() != null && c.getPhone().startsWith("91000000"))
                .forEach(customerRepository::delete);
    }

    @Test
    void create_agent_autoAssignsToSelf() throws Exception {
        mockMvc.perform(post("/api/leads").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New Lead", "phone", "9100000097", "source", "WEBSITE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("New Lead"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agentId));
    }

    @Test
    void create_admin_canAssignToAnyAgent() throws Exception {
        mockMvc.perform(post("/api/leads").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Admin Created Lead", "phone", "9100000096", "source", "REFERRAL",
                                "assignedAgentId", otherAgentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assignedAgentId").value(otherAgentId));
    }

    @Test
    void create_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/leads").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-name-phone-source@test.com"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_owningAgent_updatesFields() throws Exception {
        mockMvc.perform(put("/api/leads/" + leadId).header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Lead Name", "phone", "9100000098", "source", "COLD_CALL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Lead Name"))
                .andExpect(jsonPath("$.data.source").value("COLD_CALL"));
    }

    @Test
    void update_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/leads/" + leadId).header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Hijacked", "phone", "9100000098", "source", "REFERRAL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_agentCannotReassignToAnotherAgent() throws Exception {
        // Agents may edit their own lead's other fields but not hand it off to someone else.
        mockMvc.perform(put("/api/leads/" + leadId).header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Functional Test Lead", "phone", "9100000098", "source", "REFERRAL",
                                "assignedAgentId", otherAgentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedAgentId").value(agentId));
    }

    @Test
    void updateStatus_toContacted_setsLastContactedAt() throws Exception {
        mockMvc.perform(patch("/api/leads/" + leadId + "/status").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONTACTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"))
                .andExpect(jsonPath("$.data.lastContactedAt").isNotEmpty());
    }

    @Test
    void updateStatus_toLostWithReason_recordsReason() throws Exception {
        mockMvc.perform(patch("/api/leads/" + leadId + "/status").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "LOST", "lostReason", "Too expensive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOST"))
                .andExpect(jsonPath("$.data.lostReason").value("Too expensive"));
    }

    @Test
    void updateStatus_onAlreadyConvertedLead_returns400() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/convert").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/leads/" + leadId + "/status").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CONTACTED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convert_owningAgent_createsCustomerAndLinksIt() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/convert").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.convertedCustomerId").isNotEmpty())
                .andExpect(jsonPath("$.data.convertedAt").isNotEmpty());
    }

    @Test
    void convert_nonOwningAgent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/convert").header("Authorization", "Bearer " + otherAgentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void convert_alreadyConverted_returns400() throws Exception {
        mockMvc.perform(post("/api/leads/" + leadId + "/convert").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/leads/" + leadId + "/convert").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isBadRequest());
    }
}
