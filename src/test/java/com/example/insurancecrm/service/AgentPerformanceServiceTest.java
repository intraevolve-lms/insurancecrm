package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.response.AgentPerformanceResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPerformanceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CommunicationLogRepository communicationLogRepository;

    @InjectMocks
    private AgentPerformanceService agentPerformanceService;

    private final User agent = User.builder().id("agent-1").name("Agent One").role(Role.AGENT).active(true).build();

    // ── getPerformance scoping ───────────────────────────────────────

    @Test
    void getPerformance_admin_returnsEveryActiveAgent() {
        when(userRepository.findByRoleAndActiveTrue(Role.AGENT)).thenReturn(List.of(agent));
        when(customerRepository.findByAssignedAgentId(any())).thenReturn(List.of());
        when(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc(any())).thenReturn(Optional.empty());

        List<AgentPerformanceResponse> result = agentPerformanceService.getPerformance("admin-1", true);

        assertThat(result).hasSize(1);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getPerformance_agent_seesOnlyTheirOwnRow() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of());
        when(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc("agent-1")).thenReturn(Optional.empty());

        List<AgentPerformanceResponse> result = agentPerformanceService.getPerformance("agent-1", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo("agent-1");
        verify(userRepository, never()).findByRoleAndActiveTrue(any());
    }

    // ── outcome pooling — regression test for the "customer outcomes were invisible" bug ──

    @Test
    void buildStats_poolsOutcomesFromCustomers() {
        Customer ringingCustomer = Customer.builder().id("c1").assignedAgentId("agent-1")
                .lastOutcome(CommunicationOutcome.RINGING).build();
        Customer noOutcomeCustomer = Customer.builder().id("c2").assignedAgentId("agent-1").build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(ringingCustomer, noOutcomeCustomer));
        when(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc("agent-1")).thenReturn(Optional.empty());

        AgentPerformanceResponse stats = agentPerformanceService.getPerformance("agent-1", false).get(0);

        assertThat(stats.getRinging()).isEqualTo(1L);
        assertThat(stats.getTotalCustomers()).isEqualTo(2L);
    }

    @Test
    void buildStats_reassigningOutcomeReplacesThePreviousCount() {
        // A customer's lastOutcome is a single current-state field, not an append-only log —
        // switching Ringing -> Callback should move the count, not add to both.
        Customer customer = Customer.builder().id("c1").assignedAgentId("agent-1")
                .lastOutcome(CommunicationOutcome.CALLBACK).build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(customer));
        when(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc("agent-1")).thenReturn(Optional.empty());

        AgentPerformanceResponse stats = agentPerformanceService.getPerformance("agent-1", false).get(0);

        assertThat(stats.getCallback()).isEqualTo(1L);
        assertThat(stats.getRinging()).isZero();
    }

    @Test
    void buildStats_countsLanguageIssueOutcome() {
        Customer customer = Customer.builder().id("c1").assignedAgentId("agent-1")
                .lastOutcome(CommunicationOutcome.LANGUAGE_ISSUE).build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(customer));
        when(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc("agent-1")).thenReturn(Optional.empty());

        AgentPerformanceResponse stats = agentPerformanceService.getPerformance("agent-1", false).get(0);

        assertThat(stats.getLanguageIssue()).isEqualTo(1L);
    }
}
