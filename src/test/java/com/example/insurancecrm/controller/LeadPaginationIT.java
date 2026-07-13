package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.LeadRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers server-side pagination/status/outcome/search filtering on GET /api/leads, and the
 * full-dataset counts returned by GET /api/leads/summary — these build a dynamic MongoTemplate
 * query and aggregate count queries respectively, which a Mockito unit test can't meaningfully
 * verify, so this exercises the real queries against a real database.
 *
 * Cleanup is by tracked lead id (not by name) — see CustomerPaginationIT for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LeadPaginationIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "lpg-admin@test.com";
    private static final String AGENT_EMAIL = "lpg-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "lpg-other-agent@test.com";

    private String adminToken;
    private String agentToken;
    private final List<String> createdLeadIds = new ArrayList<>();

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

        // 25 NEW leads assigned to our test agent, for pagination math (2 pages at size 20)
        for (int i = 0; i < 25; i++) {
            createdLeadIds.add(leadRepository.save(Lead.builder()
                    .name("LPG Lead " + i).phone("92000" + String.format("%05d", i))
                    .source(LeadSource.REFERRAL).status(LeadStatus.NEW)
                    .assignedAgentId(agent.getId())
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build()).getId());
        }
        // 3 leads assigned to a different agent — must never appear in the test agent's results
        for (int i = 0; i < 3; i++) {
            createdLeadIds.add(leadRepository.save(Lead.builder()
                    .name("LPG Other Lead " + i).phone("93000" + String.format("%05d", i))
                    .source(LeadSource.REFERRAL).status(LeadStatus.NEW)
                    .assignedAgentId(otherAgent.getId())
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build()).getId());
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(OTHER_AGENT_EMAIL).ifPresent(userRepository::delete);
        if (!createdLeadIds.isEmpty()) {
            leadRepository.deleteAllById(createdLeadIds);
            createdLeadIds.clear();
        }
    }

    @Test
    void getAll_admin_firstPage_returnsTwentyOfTwentyEight() throws Exception {
        mockMvc.perform(get("/api/leads").param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(28))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getAll_agent_onlySeesOwnAssignedLeads() throws Exception {
        mockMvc.perform(get("/api/leads").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25));
    }

    @Test
    void getAll_statusFilter_returnsOnlyMatchingStatus() throws Exception {
        Lead contacted = leadRepository.findById(createdLeadIds.get(0)).orElseThrow();
        contacted.setStatus(LeadStatus.CONTACTED);
        leadRepository.save(contacted);

        mockMvc.perform(get("/api/leads").param("page", "0").param("size", "50")
                        .param("status", "CONTACTED")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(contacted.getId()));
    }

    @Test
    void getAll_outcomeFilter_excludesConvertedAndLost() throws Exception {
        Lead matching = leadRepository.findById(createdLeadIds.get(0)).orElseThrow();
        matching.setLastOutcome(CommunicationOutcome.RINGING);
        leadRepository.save(matching);

        Lead convertedButSameOutcome = leadRepository.findById(createdLeadIds.get(1)).orElseThrow();
        convertedButSameOutcome.setLastOutcome(CommunicationOutcome.RINGING);
        convertedButSameOutcome.setStatus(LeadStatus.CONVERTED);
        leadRepository.save(convertedButSameOutcome);

        mockMvc.perform(get("/api/leads").param("page", "0").param("size", "50")
                        .param("outcome", "RINGING")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()));
    }

    @Test
    void getAll_search_matchesNameOrPhone() throws Exception {
        mockMvc.perform(get("/api/leads").param("page", "0").param("size", "50")
                        .param("q", "LPG Other Lead")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void getSummary_countsReflectFullDatasetRegardlessOfPage() throws Exception {
        mockMvc.perform(get("/api/leads/summary").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.NEW").value(25))
                .andExpect(jsonPath("$.data.statusCounts.CONTACTED").value(0));
    }

    @Test
    void getSummary_admin_seesCountsAcrossAllAgents() throws Exception {
        mockMvc.perform(get("/api/leads/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.NEW").value(28));
    }
}
