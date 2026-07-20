package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.response.AgentPerformanceResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
import com.example.insurancecrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AgentPerformanceService {

    private static final Set<LeadStatus> EXCLUDED_STATUSES = EnumSet.of(LeadStatus.CONVERTED, LeadStatus.LOST);

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final CommunicationLogRepository communicationLogRepository;

    public List<AgentPerformanceResponse> getPerformance(String currentUserId, boolean isAdmin) {
        List<User> agents = isAdmin
                ? userRepository.findByRoleAndActiveTrue(Role.AGENT)
                : userRepository.findById(currentUserId).map(List::of).orElse(List.of());

        return agents.stream().map(this::buildStats).toList();
    }

    private AgentPerformanceResponse buildStats(User agent) {
        List<Customer> customers = customerRepository.findByAssignedAgentId(agent.getId());

        List<Lead> activeLeads = leadRepository.findByAssignedAgentId(agent.getId()).stream()
                .filter(l -> !EXCLUDED_STATUSES.contains(l.getStatus()))
                .toList();

        // Funnel counts pool outcomes logged against both customers and leads — Log Activity
        // updates lastOutcome on whichever entity it was logged against.
        Map<CommunicationOutcome, Long> outcomeCounts = Stream.concat(
                    customers.stream().map(Customer::getLastOutcome),
                    activeLeads.stream().map(Lead::getLastOutcome))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()));

        return AgentPerformanceResponse.builder()
                .agentId(agent.getId())
                .agentName(agent.getName())
                .totalCustomers((long) customers.size())
                .myCallback(outcomeCounts.getOrDefault(CommunicationOutcome.MY_CALLBACK, 0L))
                .callback(outcomeCounts.getOrDefault(CommunicationOutcome.CALLBACK, 0L))
                .prospect(outcomeCounts.getOrDefault(CommunicationOutcome.PROSPECT, 0L))
                .ringing(outcomeCounts.getOrDefault(CommunicationOutcome.RINGING, 0L))
                .switchOff(outcomeCounts.getOrDefault(CommunicationOutcome.SWITCH_OFF, 0L))
                .hangUp(outcomeCounts.getOrDefault(CommunicationOutcome.HANG_UP, 0L))
                .nextYear(outcomeCounts.getOrDefault(CommunicationOutcome.NEXT_YEAR, 0L))
                .languageIssue(outcomeCounts.getOrDefault(CommunicationOutcome.LANGUAGE_ISSUE, 0L))
                .lastActivityAt(communicationLogRepository.findFirstByLoggedByOrderByLoggedAtDesc(agent.getId())
                        .map(CommunicationLog::getLoggedAt)
                        .orElse(null))
                .build();
    }
}
