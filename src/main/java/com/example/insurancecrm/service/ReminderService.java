package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.ReminderResponse;
import com.example.insurancecrm.dto.response.ReminderResponse.ReminderType;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
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

    private final CommunicationLogRepository commLogRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public List<ReminderResponse> getReminders(String userId, boolean isAdmin) {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderResponse> reminders = new ArrayList<>();

        // Pre-fetch name maps
        Map<String, String> customerNames = customerRepository.findAll().stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

        // Communication log follow-up dates <= today
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
                reminders.add(ReminderResponse.builder()
                        .id(c.getId())
                        .type(ReminderType.COMMUNICATION_FOLLOWUP)
                        .entityId(c.getCustomerId())
                        .entityKind(ReminderResponse.EntityKind.CUSTOMER)
                        .entityName(customerNames.getOrDefault(c.getCustomerId(), "Customer"))
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
