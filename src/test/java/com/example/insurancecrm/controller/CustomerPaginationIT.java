package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the server-side pagination/sort/filter behavior on GET /api/customers and
 * /api/customers/search — these now build a dynamic MongoTemplate query, which a Mockito unit
 * test can't meaningfully verify, so this exercises the real query against a real database.
 *
 * Cleanup is by tracked customer id (not by name) — a prior bug where cleanup matched by name
 * leaked renamed test records into the dev database, so this deliberately avoids that pattern.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CustomerPaginationIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "pg-admin@test.com";
    private static final String AGENT_EMAIL = "pg-agent@test.com";
    private static final String OTHER_AGENT_EMAIL = "pg-other-agent@test.com";

    private String adminToken;
    private String agentToken;
    private String agentId;
    private String otherAgentId;
    private final List<String> createdCustomerIds = new ArrayList<>();

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

        // 25 customers assigned to our test agent, for pagination math (2 pages at size 20)
        for (int i = 0; i < 25; i++) {
            createdCustomerIds.add(customerRepository.save(Customer.builder()
                    .name("PG Customer " + i).phone("90000" + String.format("%05d", i))
                    .assignedAgentId(agent.getId())
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build()).getId());
        }
        // 3 customers assigned to a different agent — must never appear in the test agent's results
        for (int i = 0; i < 3; i++) {
            createdCustomerIds.add(customerRepository.save(Customer.builder()
                    .name("PG Other Customer " + i).phone("91111" + String.format("%05d", i))
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
        if (!createdCustomerIds.isEmpty()) {
            customerRepository.deleteAllById(createdCustomerIds);
            createdCustomerIds.clear();
        }
    }

    @Test
    void getAll_admin_firstPage_returnsTwentyOfTwentyEight() throws Exception {
        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(28))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void getAll_admin_secondPage_returnsRemainder() throws Exception {
        mockMvc.perform(get("/api/customers").param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(8))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void getAll_agent_onlySeesOwnAssignedCustomers() throws Exception {
        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.content.length()").value(25));
    }

    @Test
    void getAll_sortByPremium_ascending_ordersLowestBeforeHighest() throws Exception {
        // Other seeded customers have no premium at all — MongoDB's native ascending sort puts
        // those nulls first (unlike the previous client-side sort, which always pushed nulls
        // last). So this only asserts relative order between the two known values, not position 0.
        Customer low = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        low.setLastYearPremium(new BigDecimal("100"));
        customerRepository.save(low);
        Customer high = customerRepository.findById(createdCustomerIds.get(1)).orElseThrow();
        high.setLastYearPremium(new BigDecimal("999999"));
        customerRepository.save(high);

        String body = mockMvc.perform(get("/api/customers").param("page", "0").param("size", "50")
                        .param("sortBy", "premium").param("sortDir", "asc")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(low.getId())).isLessThan(ids.indexOf(high.getId()));
    }

    @Test
    void getAll_outcomeFilter_returnsOnlyMatching() throws Exception {
        Customer withOutcome = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        withOutcome.setLastOutcome(CommunicationOutcome.SALE_CLOSE);
        customerRepository.save(withOutcome);

        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "50")
                        .param("outcome", "SALE_CLOSE")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(withOutcome.getId()));
    }

    @Test
    void getAll_admin_assignedAgentIdFilter_returnsOnlyThatAgentsCustomers() throws Exception {
        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "50")
                        .param("assignedAgentId", otherAgentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void getAll_agent_assignedAgentIdFilter_isIgnored_stillOnlySeesOwnCustomers() throws Exception {
        // An agent trying to peek at another agent's customers via this param must not work —
        // agents stay hard-scoped to their own assignedAgentId regardless of what's requested.
        mockMvc.perform(get("/api/customers").param("page", "0").param("size", "50")
                        .param("assignedAgentId", otherAgentId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25));
    }

    @Test
    void search_admin_assignedAgentIdFilter_combinesWithQuery() throws Exception {
        mockMvc.perform(get("/api/customers/search").param("q", "PG")
                        .param("page", "0").param("size", "50")
                        .param("assignedAgentId", agentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25));
    }

    @Test
    void search_agent_scopedAndPaginated() throws Exception {
        mockMvc.perform(get("/api/customers/search").param("q", "PG Customer")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.content.length()").value(10));
    }

    @Test
    void search_doesNotLeakOtherAgentsCustomers() throws Exception {
        mockMvc.perform(get("/api/customers/search").param("q", "PG Other Customer")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── /new — the uncontacted work queue ────────────────────────────

    @Test
    void getNew_agent_onlySeesOwnUncontactedCustomers() throws Exception {
        // All 25 of the test agent's seeded customers have no lastOutcome yet, so all count as new.
        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25));
    }

    @Test
    void getNew_admin_seesEveryUncontactedCustomerAcrossAgents() throws Exception {
        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(28));
    }

    @Test
    void getNew_customerDropsOffTheListOnceAnOutcomeIsLogged() throws Exception {
        Customer contacted = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        contacted.setLastOutcome(CommunicationOutcome.RINGING);
        customerRepository.save(contacted);

        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(24))
                .andExpect(jsonPath("$.data.content[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(contacted.getId()))));
    }

    @Test
    void getNew_ordersOldestAssignedFirst() throws Exception {
        Customer older = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        older.setCreatedAt(LocalDateTime.now().minusDays(5));
        customerRepository.save(older);
        Customer newer = customerRepository.findById(createdCustomerIds.get(1)).orElseThrow();
        newer.setCreatedAt(LocalDateTime.now());
        customerRepository.save(newer);

        String body = mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(older.getId())).isLessThan(ids.indexOf(newer.getId()));
    }

    @Test
    void getNew_admin_includesCompletelyUnassignedCustomers() throws Exception {
        // An import that skipped assignment (or hasn't been triaged yet) still needs to surface
        // to an admin reviewing the uncontacted queue for coverage gaps.
        String unassignedId = customerRepository.save(Customer.builder()
                .name("PG Unassigned Customer").phone("92222220000")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build()).getId();
        createdCustomerIds.add(unassignedId);

        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(29))
                .andExpect(jsonPath("$.data.content[*].id").value(org.hamcrest.Matchers.hasItem(unassignedId)));
    }

    @Test
    void getNew_agent_doesNotSeeUnassignedCustomers() throws Exception {
        String unassignedId = customerRepository.save(Customer.builder()
                .name("PG Unassigned Customer 2").phone("92222220001")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build()).getId();
        createdCustomerIds.add(unassignedId);

        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.content[*].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(unassignedId))));
    }

    @Test
    void getNew_searchQuery_scopesToMatchingUncontactedCustomers() throws Exception {
        Customer target = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        target.setName("Findable New Customer");
        customerRepository.save(target);

        mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .param("q", "Findable")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(target.getId()));
    }

    @Test
    void getNew_explicitSortByPremium_overridesTheOldestFirstDefault() throws Exception {
        Customer low = customerRepository.findById(createdCustomerIds.get(0)).orElseThrow();
        low.setLastYearPremium(new BigDecimal("100"));
        customerRepository.save(low);
        Customer high = customerRepository.findById(createdCustomerIds.get(1)).orElseThrow();
        high.setLastYearPremium(new BigDecimal("999999"));
        customerRepository.save(high);

        String body = mockMvc.perform(get("/api/customers/new").param("page", "0").param("size", "50")
                        .param("sortBy", "premium").param("sortDir", "desc")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(high.getId())).isLessThan(ids.indexOf(low.getId()));
    }
}
