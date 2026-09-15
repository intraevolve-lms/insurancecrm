package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.AuditLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateCustomerRequest;
import com.example.insurancecrm.dto.response.CustomerResponse;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.AuditLogRepository;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CommunicationLogRepository communicationLogRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks
    private CustomerService customerService;

    @Captor private ArgumentCaptor<Customer> customerCaptor;
    @Captor private ArgumentCaptor<List<Customer>> customerListCaptor;

    private Customer agentOwnedCustomer;
    private Customer otherCustomer;

    @BeforeEach
    void setUp() {
        agentOwnedCustomer = Customer.builder()
                .id("cust-1").name("Owned Customer").phone("9000000001")
                .assignedAgentId("agent-1").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        otherCustomer = Customer.builder()
                .id("cust-2").name("Other Customer").phone("9000000002")
                .assignedAgentId("agent-2").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // getAllCustomers/search now build dynamic MongoTemplate queries (pagination, sort, outcome
    // filter, agent scoping) — covered by CustomerPaginationIT against a real database instead,
    // since a Mockito mock of MongoTemplate can't meaningfully verify query correctness.

    // ── findByIdForAgent (object-level access control) ──────────────

    @Test
    void findByIdForAgent_owningAgent_succeeds() {
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));

        Customer result = customerService.findByIdForAgent("cust-1", "agent-1", false);

        assertThat(result.getId()).isEqualTo("cust-1");
    }

    @Test
    void findByIdForAgent_nonOwningAgent_throwsForbidden() {
        when(customerRepository.findById("cust-2")).thenReturn(Optional.of(otherCustomer));

        assertThatThrownBy(() -> customerService.findByIdForAgent("cust-2", "agent-1", false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void findByIdForAgent_admin_succeedsRegardlessOfOwner() {
        when(customerRepository.findById("cust-2")).thenReturn(Optional.of(otherCustomer));

        Customer result = customerService.findByIdForAgent("cust-2", "admin-1", true);

        assertThat(result.getId()).isEqualTo("cust-2");
    }

    @Test
    void findByIdForAgent_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findByIdForAgent("missing", "agent-1", false))
                .isInstanceOf(ApiException.class);
    }

    // ── getByIdForView — audit trail ────────────────────────────────

    @Test
    void getByIdForView_recordsViewedAuditLogAndReturnsLastOpenedInfo() {
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        AuditLog priorView = AuditLog.builder()
                .performedByName("Someone Else").timestamp(LocalDateTime.now().minusHours(1)).build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("CUSTOMER", "cust-1"))
                .thenReturn(List.of(priorView));

        CustomerResponse response = customerService.getByIdForView("cust-1", "agent-1", "Agent One", false);

        assertThat(response.getLastOpenedByName()).isEqualTo("Someone Else");

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("VIEWED");
        assertThat(logCaptor.getValue().getPerformedBy()).isEqualTo("agent-1");
    }

    @Test
    void getByIdForView_nonOwningAgent_isForbidden() {
        when(customerRepository.findById("cust-2")).thenReturn(Optional.of(otherCustomer));

        assertThatThrownBy(() -> customerService.getByIdForView("cust-2", "agent-1", "Agent One", false))
                .isInstanceOf(ApiException.class);
    }

    // ── create / update ──────────────────────────────────────────────

    @Test
    void create_normalizesPhoneNumber() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setName("New Customer");
        req.setPhone("+91 90000 00003");

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        customerService.create(req);

        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getPhone()).isEqualTo("9000000003");
    }

    @Test
    void update_appliesRequestedAssignedAgentIdWhenProvided() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setName("Updated Name");
        req.setPhone("9000000001");
        req.setAssignedAgentId("agent-9");

        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        customerService.update("cust-1", req);

        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getAssignedAgentId()).isEqualTo("agent-9");
        assertThat(customerCaptor.getValue().getName()).isEqualTo("Updated Name");
    }

    @Test
    void update_leavesExistingAssignedAgentWhenNotProvided() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setName("Updated Name");
        req.setPhone("9000000001");
        req.setAssignedAgentId(null);

        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        customerService.update("cust-1", req);

        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getAssignedAgentId()).isEqualTo("agent-1");
    }

    // ── assignAgent / bulkAssignAgent ────────────────────────────────

    @Test
    void assignAgent_missingAgent_throwsNotFound() {
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(userRepository.findByIdAndDeletedAtIsNull("missing-agent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.assignAgent("cust-1", "missing-agent"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assignAgent_setsAgentOnCustomer() {
        User agent = User.builder().id("agent-9").name("Agent Nine").role(Role.AGENT).build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(userRepository.findByIdAndDeletedAtIsNull("agent-9")).thenReturn(Optional.of(agent));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(agent));

        CustomerResponse response = customerService.assignAgent("cust-1", "agent-9");

        assertThat(response.getAssignedAgentId()).isEqualTo("agent-9");
        assertThat(response.getAssignedAgentName()).isEqualTo("Agent Nine");
    }

    @Test
    void getById_softDeletedAgent_showsCustomerAsUnassignedNotTheDeletedAgentsName() {
        // enrichAndMap deliberately filters out soft-deleted users when resolving names, so a
        // customer still pointing at one shows as unassigned rather than displaying a ghost name.
        User deletedAgent = User.builder().id("agent-1").name("Departed Agent").role(Role.AGENT)
                .deletedAt(LocalDateTime.now()).build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(deletedAgent));

        CustomerResponse response = customerService.getById("cust-1");

        assertThat(response.getAssignedAgentName()).isNull();
        assertThat(response.getAssignedAgentId()).isEqualTo("agent-1");
    }

    @Test
    void bulkAssignAgent_skipsCustomerIdsThatDoNotExist() {
        User agent = User.builder().id("agent-9").name("Agent Nine").role(Role.AGENT).build();
        when(userRepository.findByIdAndDeletedAtIsNull("agent-9")).thenReturn(Optional.of(agent));
        when(customerRepository.findAllById(List.of("cust-1", "missing")))
                .thenReturn(List.of(agentOwnedCustomer));
        when(customerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(agent));

        var result = customerService.bulkAssignAgent(List.of("cust-1", "missing"), "agent-9");

        assertThat(result.getAssignedCount()).isEqualTo(1);
        assertThat(result.getNotFoundCustomerIds()).containsExactly("missing");
    }

    @Test
    void bulkAssignAgent_missingAgent_throwsNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull("missing-agent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.bulkAssignAgent(List.of("cust-1"), "missing-agent"))
                .isInstanceOf(ApiException.class);
    }

    // ── reassignAllCustomers — agent offboarding ─────────────────────

    @Test
    void reassignAllCustomers_movesEveryCustomerFromOneAgentToAnother() {
        User toAgent = User.builder().id("agent-9").name("Agent Nine").role(Role.AGENT).build();
        when(userRepository.findByIdAndDeletedAtIsNull("agent-9")).thenReturn(Optional.of(toAgent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(agentOwnedCustomer));
        when(customerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var result = customerService.reassignAllCustomers("agent-1", "agent-9");

        assertThat(result.getReassignedCount()).isEqualTo(1);
        assertThat(result.getToAgentName()).isEqualTo("Agent Nine");
        verify(customerRepository).saveAll(customerListCaptor.capture());
        assertThat(customerListCaptor.getValue().get(0).getAssignedAgentId()).isEqualTo("agent-9");
    }

    @Test
    void reassignAllCustomers_missingToAgent_throwsNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull("missing-agent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.reassignAllCustomers("agent-1", "missing-agent"))
                .isInstanceOf(ApiException.class);
        verify(customerRepository, never()).findByAssignedAgentId(any());
    }

    @Test
    void reassignAllCustomers_fromAgentDoesNotNeedToExist_stillReassignsOrphanedCustomers() {
        // The whole point: this must work even after the source agent's account is already gone,
        // since that's exactly when a customer's assignedAgentId is left as a dangling reference.
        User toAgent = User.builder().id("agent-9").name("Agent Nine").role(Role.AGENT).build();
        when(userRepository.findByIdAndDeletedAtIsNull("agent-9")).thenReturn(Optional.of(toAgent));
        when(customerRepository.findByAssignedAgentId("deleted-agent-id")).thenReturn(List.of(agentOwnedCustomer));
        when(customerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var result = customerService.reassignAllCustomers("deleted-agent-id", "agent-9");

        assertThat(result.getReassignedCount()).isEqualTo(1);
        verify(userRepository, never()).findById("deleted-agent-id");
    }

    @Test
    void reassignAllCustomers_noCustomersAssigned_returnsZeroCount() {
        User toAgent = User.builder().id("agent-9").name("Agent Nine").role(Role.AGENT).build();
        when(userRepository.findByIdAndDeletedAtIsNull("agent-9")).thenReturn(Optional.of(toAgent));
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of());
        when(customerRepository.saveAll(anyList())).thenReturn(List.of());

        var result = customerService.reassignAllCustomers("agent-1", "agent-9");

        assertThat(result.getReassignedCount()).isZero();
    }

    // ── delete ───────────────────────────────────────────────────────

    @Test
    void delete_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.delete("missing")).isInstanceOf(ApiException.class);
        verify(customerRepository, never()).delete(any());
    }

    @Test
    void delete_existingCustomer_deletesIt() {
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));

        customerService.delete("cust-1");

        verify(customerRepository).delete(agentOwnedCustomer);
    }

    @Test
    void delete_alsoCascadesCommunicationLogs() {
        // Regression: a customer's logged calls used to survive deletion, leaving a follow-up
        // reminder pointing at a customer that no longer exists (see ReminderService).
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(agentOwnedCustomer));

        customerService.delete("cust-1");

        verify(communicationLogRepository).deleteByCustomerId("cust-1");
    }

    @Test
    void bulkDelete_alsoCascadesCommunicationLogsForEachDeletedCustomer() {
        when(customerRepository.findAllById(List.of("cust-1")))
                .thenReturn(List.of(agentOwnedCustomer));

        customerService.bulkDelete(List.of("cust-1"));

        verify(communicationLogRepository).deleteByCustomerIdIn(List.of("cust-1"));
    }
}
