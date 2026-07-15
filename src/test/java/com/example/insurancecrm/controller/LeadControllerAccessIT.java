package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.Role;
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
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only DELETE is admin-gated on LeadController; everything else is open to any authenticated
 *  user with object-level scoping enforced in LeadService (covered by LeadServiceTest).
 *  MockMvc is built manually since this Spring Boot version doesn't bundle the web/servlet test slice. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LeadControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "lc-admin@test.com";
    private static final String AGENT_EMAIL = "lc-agent@test.com";

    private String adminToken;
    private String agentToken;
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

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");

        Lead lead = leadRepository.save(Lead.builder()
                .name("Access Test Lead").phone("9000000088").source(LeadSource.REFERRAL)
                .status(LeadStatus.NEW).assignedAgentId(agent.getId())
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
        leadRepository.findAll().stream()
                .filter(l -> "Access Test Lead".equals(l.getName()))
                .forEach(leadRepository::delete);
    }

    @Test
    void getAll_agent_isAllowed() throws Exception {
        mockMvc.perform(get("/api/leads").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    @Test
    void delete_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/leads/" + leadId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_admin_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/leads/" + leadId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getById_ownAssignedLead_agent_isAllowed() throws Exception {
        mockMvc.perform(get("/api/leads/" + leadId).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
    }

    @Test
    void bulkDelete_agent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/leads/bulk-delete").header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(leadId)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkDelete_admin_isAllowed_andReportsNotFoundIds() throws Exception {
        mockMvc.perform(delete("/api/leads/bulk-delete").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(leadId, "missing-id")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(1))
                .andExpect(jsonPath("$.data.notFoundIds[0]").value("missing-id"));
    }
}
