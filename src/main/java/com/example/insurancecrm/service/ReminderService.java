package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.dto.response.ReminderResponse;
import com.example.insurancecrm.dto.response.ReminderResponse.ReminderType;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
import com.example.insurancecrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final LeadRepository leadRepository;
    private final CommunicationLogRepository commLogRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public List<ReminderResponse> getReminders(String userId, boolean isAdmin) {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderResponse> reminders = new ArrayList<>();

        // Pre-fetch name maps
        Map<String, String> customerNames = customerRepository.findAll().stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        Map<String, String> leadNames = leadRepository.findAll().stream()
                .collect(Collectors.toMap(Lead::getId, Lead::getName));
        Map<String, String> userNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getName()));

        // ── 1. Lead follow-up dates <= today ───────────────────────────────
        List<Lead> leads = isAdmin
                ? leadRepository.findAll()
                : leadRepository.findByAssignedAgentId(userId);

        for (Lead l : leads) {
            if (l.getFollowUpDate() == null) continue;
            if (l.getStatus() == LeadStatus.CONVERTED || l.getStatus() == LeadStatus.LOST) continue;
            if (!l.getFollowUpDate().isAfter(now)) {
                long overdue = ChronoUnit.DAYS.between(l.getFollowUpDate().toLocalDate(), now.toLocalDate());
                reminders.add(ReminderResponse.builder()
                        .id(l.getId())
                        .type(ReminderType.LEAD_FOLLOWUP)
                        .entityId(l.getId())
                        .entityKind(ReminderResponse.EntityKind.LEAD)
                        .entityName(l.getName())
                        .description("Follow up with lead" + (l.getPhone() != null ? " · " + l.getPhone() : ""))
                        .dueDate(l.getFollowUpDate())
                        .overdueDays(overdue)
                        .assignedToId(l.getAssignedAgentId())
                        .assignedToName(l.getAssignedAgentId() != null ? userNames.get(l.getAssignedAgentId()) : null)
                        .build());
            }
        }

        // ── 2. Communication log follow-up dates <= today ──────────────────
        List<CommunicationLog> comms = commLogRepository.findAll();
        if (!isAdmin) {
            comms = comms.stream()
                    .filter(c -> userId.equals(c.getLoggedBy()))
                    .toList();
        }

        for (CommunicationLog c : comms) {
            if (c.getFollowUpDate() == null) continue;
            if (!c.getFollowUpDate().isAfter(now)) {
                long overdue = ChronoUnit.DAYS.between(c.getFollowUpDate().toLocalDate(), now.toLocalDate());
                boolean isCustomer = c.getCustomerId() != null;
                String entityName = isCustomer
                        ? customerNames.getOrDefault(c.getCustomerId(), "Customer")
                        : leadNames.getOrDefault(c.getLeadId(), "Lead");
                reminders.add(ReminderResponse.builder()
                        .id(c.getId())
                        .type(ReminderType.COMMUNICATION_FOLLOWUP)
                        .entityId(isCustomer ? c.getCustomerId() : c.getLeadId())
                        .entityKind(isCustomer ? ReminderResponse.EntityKind.CUSTOMER : ReminderResponse.EntityKind.LEAD)
                        .entityName(entityName)
                        .description("Follow up after " + c.getChannel().name().toLowerCase().replace("_", " "))
                        .dueDate(c.getFollowUpDate())
                        .overdueDays(overdue)
                        .assignedToId(c.getLoggedBy())
                        .assignedToName(c.getLoggedByName())
                        .build());
            }
        }

        // Sort: overdue first (most overdue at top), then due today
        reminders.sort(Comparator.comparingLong(ReminderResponse::getOverdueDays).reversed());
        return reminders;
    }
}
