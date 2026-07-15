package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.dto.response.ReminderResponse;
import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
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

    @Mock private LeadRepository leadRepository;
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

    private Lead lead(String id, LocalDateTime followUpDate, LeadStatus status) {
        return Lead.builder().id(id).name("Lead " + id).phone("900000000" + id.hashCode() % 10)
                .followUpDate(followUpDate).status(status).assignedAgentId("agent-1").build();
    }

    @Test
    void getReminders_leadFollowUpDueToday_isIncluded() {
        when(leadRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(lead("l1", today, LeadStatus.NEW)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOverdueDays()).isZero();
        assertThat(result.get(0).getType()).isEqualTo(ReminderResponse.ReminderType.LEAD_FOLLOWUP);
        assertThat(result.get(0).getEntityKind()).isEqualTo(ReminderResponse.EntityKind.LEAD);
    }

    @Test
    void getReminders_leadFollowUpOverdue_calculatesOverdueDays() {
        when(leadRepository.findByAssignedAgentId("agent-1"))
                .thenReturn(List.of(lead("l1", today.minusDays(3), LeadStatus.NEW)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result.get(0).getOverdueDays()).isEqualTo(3);
    }

    @Test
    void getReminders_leadFollowUpInFuture_isExcluded() {
        when(leadRepository.findByAssignedAgentId("agent-1"))
                .thenReturn(List.of(lead("l1", today.plusDays(2), LeadStatus.NEW)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_convertedLead_isExcludedEvenIfFollowUpDueOrOverdue() {
        when(leadRepository.findByAssignedAgentId("agent-1"))
                .thenReturn(List.of(lead("l1", today.minusDays(1), LeadStatus.CONVERTED)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_lostLead_isExcluded() {
        when(leadRepository.findByAssignedAgentId("agent-1"))
                .thenReturn(List.of(lead("l1", today.minusDays(1), LeadStatus.LOST)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_leadWithNoFollowUpDate_isExcluded() {
        when(leadRepository.findByAssignedAgentId("agent-1"))
                .thenReturn(List.of(lead("l1", null, LeadStatus.NEW)));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).isEmpty();
    }

    @Test
    void getReminders_agent_onlySeesOwnLeads() {
        // Note: leadRepository.findAll() is still called separately to build the lead-name lookup
        // map used for communication-log reminders — only the reminder-generating query is scoped.
        when(leadRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(lead("l1", today, LeadStatus.NEW)));
        when(leadRepository.findAll()).thenReturn(List.of());

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).hasSize(1);
        org.mockito.Mockito.verify(leadRepository).findByAssignedAgentId("agent-1");
    }

    @Test
    void getReminders_admin_seesAllLeads() {
        when(leadRepository.findAll()).thenReturn(List.of(lead("l1", today, LeadStatus.NEW)));

        List<ReminderResponse> result = reminderService.getReminders("admin-1", true);

        assertThat(result).hasSize(1);
        org.mockito.Mockito.verify(leadRepository, org.mockito.Mockito.never()).findByAssignedAgentId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getReminders_communicationLogFollowUp_agentSeesOnlyOwnLoggedEntries() {
        when(leadRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of());
        CommunicationLog ownLog = CommunicationLog.builder().id("log-1").customerId("cust-1")
                .channel(CommunicationChannel.CALL).followUpDate(today).loggedBy("agent-1").loggedByName("Agent One").build();
        CommunicationLog otherLog = CommunicationLog.builder().id("log-2").customerId("cust-2")
                .channel(CommunicationChannel.CALL).followUpDate(today).loggedBy("agent-2").loggedByName("Agent Two").build();
        when(commLogRepository.findAll()).thenReturn(List.of(ownLog, otherLog));
        when(customerRepository.findAll()).thenReturn(List.of(
                Customer.builder().id("cust-1").name("Customer One").build(),
                Customer.builder().id("cust-2").name("Customer Two").build()));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityName()).isEqualTo("Customer One");
        assertThat(result.get(0).getEntityKind()).isEqualTo(ReminderResponse.EntityKind.CUSTOMER);
    }

    @Test
    void getReminders_communicationLogFollowUp_admin_seesAllLoggedEntries() {
        when(leadRepository.findAll()).thenReturn(List.of());
        CommunicationLog log1 = CommunicationLog.builder().id("log-1").customerId("cust-1")
                .channel(CommunicationChannel.CALL).followUpDate(today).loggedBy("agent-1").build();
        CommunicationLog log2 = CommunicationLog.builder().id("log-2").leadId("lead-2")
                .channel(CommunicationChannel.CALL).followUpDate(today).loggedBy("agent-2").build();
        when(commLogRepository.findAll()).thenReturn(List.of(log1, log2));
        when(customerRepository.findAll()).thenReturn(List.of(Customer.builder().id("cust-1").name("Customer One").build()));

        List<ReminderResponse> result = reminderService.getReminders("admin-1", true);

        assertThat(result).hasSize(2);
    }

    @Test
    void getReminders_communicationLogFollowUp_leadEntity_resolvesLeadName() {
        when(leadRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of());
        CommunicationLog log = CommunicationLog.builder().id("log-1").leadId("lead-9")
                .channel(CommunicationChannel.CALL).followUpDate(today).loggedBy("agent-1").build();
        when(commLogRepository.findAll()).thenReturn(List.of(log));
        // leadNames map is built from leadRepository.findAll() unconditionally at the top of getReminders
        when(leadRepository.findAll()).thenReturn(List.of(
                Lead.builder().id("lead-9").name("Prospective Lead").build()));

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result.get(0).getEntityName()).isEqualTo("Prospective Lead");
        // Regression: the FE previously had no way to tell a COMMUNICATION_FOLLOWUP reminder
        // logged against a lead apart from one logged against a customer, and mis-routed clicks
        // on lead reminders to /customers/{leadId}.
        assertThat(result.get(0).getEntityKind()).isEqualTo(ReminderResponse.EntityKind.LEAD);
    }

    @Test
    void getReminders_sortedMostOverdueFirst() {
        when(leadRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(
                lead("l1", today, LeadStatus.NEW),                 // 0 days overdue
                lead("l2", today.minusDays(5), LeadStatus.NEW),     // 5 days overdue
                lead("l3", today.minusDays(1), LeadStatus.NEW)));   // 1 day overdue

        List<ReminderResponse> result = reminderService.getReminders("agent-1", false);

        assertThat(result).extracting(ReminderResponse::getOverdueDays).containsExactly(5L, 1L, 0L);
    }
}
