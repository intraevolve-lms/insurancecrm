package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.dto.request.CreateLeadRequest;
import com.example.insurancecrm.dto.request.UpdateLeadStatusRequest;
import com.example.insurancecrm.dto.response.LeadResponse;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.LeadRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private LeadRepository leadRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CommunicationLogRepository communicationLogRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks
    private LeadService leadService;

    private Lead agentOwnedLead;

    @BeforeEach
    void setUp() {
        agentOwnedLead = Lead.builder()
                .id("lead-1").name("Owned Lead").phone("9000000001")
                .status(LeadStatus.NEW).assignedAgentId("agent-1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        lenient().when(userRepository.findAllById(anyList())).thenReturn(List.of());
    }

    // getAll now builds a dynamic MongoTemplate query (pagination, status/outcome/search filters,
    // agent scoping) — covered by LeadPaginationIT against a real database instead, since a
    // Mockito mock of MongoTemplate can't meaningfully verify query correctness. getSummary's
    // count-based aggregation is likewise covered there.

    @Test
    void getById_nonOwningAgent_throwsForbidden() {
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        assertThatThrownBy(() -> leadService.getById("lead-1", "agent-2", false))
                .isInstanceOf(ApiException.class);
    }

    // ── create ─────────────────────────────────────────────────────

    @Test
    void create_agent_isAlwaysAssignedToThemselvesRegardlessOfRequest() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("New Lead");
        req.setPhone("9000000005");
        req.setSource(LeadSource.REFERRAL);
        req.setAssignedAgentId("someone-else"); // an agent should not be able to hand off to another agent

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        leadService.create(req, "agent-1", false);
        verify(leadRepository).save(captor.capture());

        assertThat(captor.getValue().getAssignedAgentId()).isEqualTo("agent-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    void create_admin_canAssignToAnyAgent() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("New Lead");
        req.setPhone("9000000005");
        req.setSource(LeadSource.REFERRAL);
        req.setAssignedAgentId("agent-9");

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        leadService.create(req, "admin-1", true);
        verify(leadRepository).save(captor.capture());

        assertThat(captor.getValue().getAssignedAgentId()).isEqualTo("agent-9");
    }

    @Test
    void create_normalizesPhoneNumber() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("New Lead");
        req.setPhone("+91 90000 00005");
        req.setSource(LeadSource.REFERRAL);

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        leadService.create(req, "agent-1", false);
        verify(leadRepository).save(captor.capture());

        assertThat(captor.getValue().getPhone()).isEqualTo("9000000005");
    }

    // ── update — reassignment is admin-only ─────────────────────────

    @Test
    void update_agent_cannotReassignLeadToAnotherAgent() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("Owned Lead");
        req.setPhone("9000000001");
        req.setSource(LeadSource.REFERRAL);
        req.setAssignedAgentId("agent-9");

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.update("lead-1", req, "agent-1", false);

        assertThat(agentOwnedLead.getAssignedAgentId()).isEqualTo("agent-1");
    }

    @Test
    void update_admin_canReassignLead() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("Owned Lead");
        req.setPhone("9000000001");
        req.setSource(LeadSource.REFERRAL);
        req.setAssignedAgentId("agent-9");

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.update("lead-1", req, "admin-1", true);

        assertThat(agentOwnedLead.getAssignedAgentId()).isEqualTo("agent-9");
    }

    @Test
    void update_nonOwningAgent_isForbidden() {
        CreateLeadRequest req = new CreateLeadRequest();
        req.setName("x"); req.setPhone("9000000001"); req.setSource(LeadSource.REFERRAL);
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        assertThatThrownBy(() -> leadService.update("lead-1", req, "agent-2", false))
                .isInstanceOf(ApiException.class);
    }

    // ── updateStatus ─────────────────────────────────────────────────

    @Test
    void updateStatus_cannotChangeStatusOfConvertedLead() {
        agentOwnedLead.setStatus(LeadStatus.CONVERTED);
        UpdateLeadStatusRequest req = new UpdateLeadStatusRequest();
        req.setStatus(LeadStatus.CONTACTED);
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        assertThatThrownBy(() -> leadService.updateStatus("lead-1", req, "agent-1", false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateStatus_toContacted_stampsLastContactedAt() {
        UpdateLeadStatusRequest req = new UpdateLeadStatusRequest();
        req.setStatus(LeadStatus.CONTACTED);
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.updateStatus("lead-1", req, "agent-1", false);

        assertThat(agentOwnedLead.getLastContactedAt()).isNotNull();
    }

    @Test
    void updateStatus_toLost_capturesLostReason() {
        UpdateLeadStatusRequest req = new UpdateLeadStatusRequest();
        req.setStatus(LeadStatus.LOST);
        req.setLostReason("Went with a competitor");
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.updateStatus("lead-1", req, "agent-1", false);

        assertThat(agentOwnedLead.getLostReason()).isEqualTo("Went with a competitor");
    }

    // ── convertToCustomer ────────────────────────────────────────────

    @Test
    void convertToCustomer_createsCustomerAndMarksLeadConverted() {
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(inv -> {
                    Customer c = inv.getArgument(0);
                    c.setId("new-cust-id");
                    return c;
                });
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.convertToCustomer("lead-1", "agent-1", false);

        assertThat(agentOwnedLead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(agentOwnedLead.getConvertedCustomerId()).isEqualTo("new-cust-id");
        assertThat(agentOwnedLead.getConvertedAt()).isNotNull();

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getAssignedAgentId()).isEqualTo("agent-1");
        assertThat(customerCaptor.getValue().getPhone()).isEqualTo(agentOwnedLead.getPhone());
    }

    @Test
    void convertToCustomer_alreadyConverted_throwsBadRequest() {
        agentOwnedLead.setStatus(LeadStatus.CONVERTED);
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        assertThatThrownBy(() -> leadService.convertToCustomer("lead-1", "agent-1", false))
                .isInstanceOf(ApiException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void convertToCustomer_nonOwningAgent_isForbidden() {
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        assertThatThrownBy(() -> leadService.convertToCustomer("lead-1", "agent-2", false))
                .isInstanceOf(ApiException.class);
        verify(customerRepository, never()).save(any());
    }

    // ── delete ───────────────────────────────────────────────────────

    @Test
    void delete_missingLead_throwsNotFound() {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.delete("missing")).isInstanceOf(ApiException.class);
    }

    @Test
    void delete_alsoCascadesCommunicationLogs() {
        // Regression: a lead's logged calls used to survive deletion, leaving a follow-up
        // reminder pointing at a lead that no longer exists (see ReminderService).
        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(agentOwnedLead));

        leadService.delete("lead-1");

        verify(communicationLogRepository).deleteByLeadId("lead-1");
    }

    @Test
    void bulkDelete_alsoCascadesCommunicationLogsForEachDeletedLead() {
        when(leadRepository.findAllById(List.of("lead-1")))
                .thenReturn(List.of(agentOwnedLead));

        leadService.bulkDelete(List.of("lead-1"));

        verify(communicationLogRepository).deleteByLeadIdIn(List.of("lead-1"));
    }
}
