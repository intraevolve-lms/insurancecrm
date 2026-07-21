package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.ReminderResponse;
import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock private CommunicationLogRepository commLogRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ReminderService reminderService;

    private final LocalDateTime today = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        lenient().when(customerRepository.findAll()).thenReturn(List.of());
        lenient().when(userRepository.findAll()).thenReturn(List.of());
        lenient().when(commLogRepository.findAll()).thenReturn(List.of());
    }

    private CommunicationLog log(String id, String customerId, String loggedBy, LocalDateTime followUpDate) {
        return CommunicationLog.builder().id(id).customerId(customerId)
                .channel(CommunicationChannel.CALL).followUpDate(followUpDate)
                .loggedBy(loggedBy).loggedByName("Agent").build();
    }

    @Test
    void getReminders_communicationLogFollowUp_agentSeesOnlyOwnLoggedEntries() {
        CommunicationLog ownLog = log("log-1", "cust-1", "agent-1", today);
        CommunicationLog otherLog = log("log-2", "cust-2", "agent-2", today);
        when(commLogRepository.findAll()).thenReturn(List.of(ownLog, otherLog));
        when(customerRepository.findAll()).thenReturn(List.of(
                Customer.builder().id("cust-1").name("Customer One").build(),
                Customer.builder().id("cust-2").name("Customer Two").build()));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityName()).isEqualTo("Customer One");
        assertThat(result.get(0).getEntityKind()).isEqualTo(ReminderResponse.EntityKind.CUSTOMER);
        assertThat(result.get(0).getType()).isEqualTo(ReminderResponse.ReminderType.COMMUNICATION_FOLLOWUP);
    }

    @Test
    void getReminders_communicationLogFollowUp_admin_seesAllLoggedEntries() {
        CommunicationLog log1 = log("log-1", "cust-1", "agent-1", today);
        CommunicationLog log2 = log("log-2", "cust-2", "agent-2", today);
        when(commLogRepository.findAll()).thenReturn(List.of(log1, log2));
        when(customerRepository.findAll()).thenReturn(List.of(Customer.builder().id("cust-1").name("Customer One").build()));

        List<ReminderResponse> result = reminderService.getReminders("admin-1", true);

        assertThat(result).hasSize(2);
    }

    @Test
    void getReminders_followUpInFuture_isExcluded() {
        when(commLogRepository.findAll()).thenReturn(List.of(log("log-1", "cust-1", "agent-1", today.plusDays(2))));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_followUpOverdue_calculatesOverdueDays() {
        when(commLogRepository.findAll()).thenReturn(List.of(log("log-1", "cust-1", "agent-1", today.minusDays(3))));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result.get(0).getOverdueDays()).isEqualTo(3);
    }

    @Test
    void getReminders_noFollowUpDate_isExcluded() {
        when(commLogRepository.findAll()).thenReturn(List.of(log("log-1", "cust-1", "agent-1", null)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_sortedMostOverdueFirst() {
        when(commLogRepository.findAll()).thenReturn(List.of(
                log("log-1", "cust-1", "agent-1", today),                 // 0 days overdue
                log("log-2", "cust-2", "agent-1", today.minusDays(5)),    // 5 days overdue
                log("log-3", "cust-3", "agent-1", today.minusDays(1))));  // 1 day overdue

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).extracting(ReminderResponse::getOverdueDays).containsExactly(5L, 1L, 0L);
    }
}
