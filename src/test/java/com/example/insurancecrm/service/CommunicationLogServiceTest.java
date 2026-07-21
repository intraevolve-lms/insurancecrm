package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateCommunicationLogRequest;
import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunicationLogServiceTest {

    @Mock private CommunicationLogRepository logRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CommunicationLogService communicationLogService;

    private final User agent = User.builder().id("agent-1").name("Agent One").build();

    private CreateCommunicationLogRequest req(CommunicationOutcome outcome) {
        CreateCommunicationLogRequest r = new CreateCommunicationLogRequest();
        r.setChannel(CommunicationChannel.CALL);
        r.setOutcome(outcome);
        return r;
    }

    // ── logForCustomer — regression test for "customer outcomes invisible on Agent Performance" ──

    @Test
    void logForCustomer_updatesCustomerLastOutcome() {
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.RINGING), "agent-1", false);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getLastOutcome()).isEqualTo(CommunicationOutcome.RINGING);
    }

    @Test
    void logForCustomer_savesLogWithCorrectCustomerIdAndLoggedBy() {
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.CALLBACK), "agent-1", false);

        assertThat(response.getCustomerId()).isEqualTo("cust-1");
        assertThat(response.getLoggedBy()).isEqualTo("agent-1");
        assertThat(response.getLoggedByName()).isEqualTo("Agent One");
        assertThat(response.getOutcome()).isEqualTo(CommunicationOutcome.CALLBACK);
    }

    @Test
    void logForCustomer_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.logForCustomer("missing", req(CommunicationOutcome.RINGING), "agent-1", false))
                .isInstanceOf(ApiException.class);
        verify(logRepository, never()).save(any());
    }

    @Test
    void logForCustomer_nonOwningAgent_isForbidden() {
        // Regression: this endpoint previously let any authenticated agent log against any
        // customer, not just their own assigned ones — the same class of leak already fixed
        // for customer search.
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.RINGING), "agent-2", false))
                .isInstanceOf(ApiException.class);
        verify(logRepository, never()).save(any());
    }

    @Test
    void logForCustomer_admin_canLogAgainstAnyCustomer() {
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        User admin = User.builder().id("admin-1").name("Admin One").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(logRepository.save(any(CommunicationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = communicationLogService.logForCustomer("cust-1", req(CommunicationOutcome.RINGING), "admin-1", true);

        assertThat(response.getLoggedBy()).isEqualTo("admin-1");
    }

    // ── getByCustomer ─────────────────────────────────────────────────

    @Test
    void getByCustomer_missingCustomer_throwsNotFound() {
        when(customerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communicationLogService.getByCustomer("missing", "agent-1", false)).isInstanceOf(ApiException.class);
    }

    @Test
    void getByCustomer_owningAgent_succeeds() {
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(logRepository.findByCustomerIdOrderByLoggedAtDesc("cust-1")).thenReturn(java.util.List.of());

        assertThat(communicationLogService.getByCustomer("cust-1", "agent-1", false)).isEmpty();
    }

    @Test
    void getByCustomer_nonOwningAgent_isForbidden() {
        // Regression: previously any authenticated agent could read any customer's call
        // history by ID, regardless of assignment.
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> communicationLogService.getByCustomer("cust-1", "agent-2", false))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void getByCustomer_admin_canReadAnyCustomer() {
        Customer customer = Customer.builder().id("cust-1").assignedAgentId("agent-1").build();
        when(customerRepository.findById("cust-1")).thenReturn(Optional.of(customer));
        when(logRepository.findByCustomerIdOrderByLoggedAtDesc("cust-1")).thenReturn(java.util.List.of());

        assertThat(communicationLogService.getByCustomer("cust-1", "admin-1", true)).isEmpty();
    }

    // ── delete ──────────────────────────────────────────────────────

    @Test
    void delete_ownLog_succeeds() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        communicationLogService.delete("log-1", "agent-1", false);

        verify(logRepository).delete(log);
    }

    @Test
    void delete_otherAgentsLog_isForbiddenForNonAdmin() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> communicationLogService.delete("log-1", "agent-2", false))
                .isInstanceOf(ApiException.class);
        verify(logRepository, never()).delete(any());
    }

    @Test
    void delete_admin_canDeleteAnyAgentsLog() {
        CommunicationLog log = CommunicationLog.builder().id("log-1").loggedBy("agent-1").build();
        when(logRepository.findById("log-1")).thenReturn(Optional.of(log));

        communicationLogService.delete("log-1", "admin-1", true);

        verify(logRepository).delete(log);
    }
}
