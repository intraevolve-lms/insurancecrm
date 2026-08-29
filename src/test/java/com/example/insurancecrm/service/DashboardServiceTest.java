package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.DashboardSummaryResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CommunicationLogRepository communicationLogRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private void stubNoSalesThisMonth() {
        when(communicationLogRepository.findByOutcomeAndLoggedAtBetween(eq(CommunicationOutcome.SALE_CLOSE), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void getSummary_admin_countsAllCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of(
                Customer.builder().id("c1").build(),
                Customer.builder().id("c2").build()));
        stubNoSalesThisMonth();

        DashboardSummaryResponse summary = dashboardService.getSummary("admin-1", true);

        assertThat(summary.getTotalCustomers()).isEqualTo(2);
        verify(customerRepository, never()).findByAssignedAgentId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getSummary_agent_countsOnlyOwnCustomers() {
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(
                Customer.builder().id("c1").assignedAgentId("agent-1").build()));
        when(communicationLogRepository.findByOutcomeAndLoggedByAndLoggedAtBetween(
                eq(CommunicationOutcome.SALE_CLOSE), eq("agent-1"), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse summary = dashboardService.getSummary("agent-1", false);

        assertThat(summary.getTotalCustomers()).isEqualTo(1);
    }

    @Test
    void getSummary_outcomeCountsAreCustomerOnly_leadsPlayNoPartHere() {
        Customer ringing = Customer.builder().id("c1").lastOutcome(CommunicationOutcome.RINGING).build();
        Customer noOutcome = Customer.builder().id("c2").build();
        when(customerRepository.findAll()).thenReturn(List.of(ringing, noOutcome));
        stubNoSalesThisMonth();

        DashboardSummaryResponse summary = dashboardService.getSummary("admin-1", true);

        assertThat(summary.getOutcomeCounts()).containsEntry(CommunicationOutcome.RINGING, 1L);
        assertThat(summary.getOutcomeCounts()).hasSize(1);
    }

    @Test
    void getSummary_noCustomers_returnsEmptyOutcomeCounts() {
        when(customerRepository.findAll()).thenReturn(List.of());
        stubNoSalesThisMonth();

        DashboardSummaryResponse summary = dashboardService.getSummary("admin-1", true);

        assertThat(summary.getTotalCustomers()).isZero();
        assertThat(summary.getOutcomeCounts()).isEmpty();
    }

    // ── totalSaleClosedThisMonth ──────────────────────────────────────

    @Test
    void getSummary_admin_sumsPremiumAcrossAllSaleCloseLogsThisMonth() {
        when(customerRepository.findAll()).thenReturn(List.of());
        when(communicationLogRepository.findByOutcomeAndLoggedAtBetween(eq(CommunicationOutcome.SALE_CLOSE), any(), any()))
                .thenReturn(List.of(
                        CommunicationLog.builder().id("l1").premium(new BigDecimal("25000")).build(),
                        CommunicationLog.builder().id("l2").premium(new BigDecimal("15000.50")).build()));

        DashboardSummaryResponse summary = dashboardService.getSummary("admin-1", true);

        assertThat(summary.getTotalSaleClosedThisMonth()).isEqualByComparingTo("40000.50");
    }

    @Test
    void getSummary_agent_sumsOnlyTheirOwnSaleCloseLogs() {
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of());
        when(communicationLogRepository.findByOutcomeAndLoggedByAndLoggedAtBetween(
                eq(CommunicationOutcome.SALE_CLOSE), eq("agent-1"), any(), any()))
                .thenReturn(List.of(CommunicationLog.builder().id("l1").premium(new BigDecimal("5000")).build()));

        DashboardSummaryResponse summary = dashboardService.getSummary("agent-1", false);

        assertThat(summary.getTotalSaleClosedThisMonth()).isEqualByComparingTo("5000");
        verify(communicationLogRepository, never())
                .findByOutcomeAndLoggedAtBetween(any(), any(), any());
    }

    @Test
    void getSummary_noSaleCloseLogsThisMonth_totalIsZero() {
        when(customerRepository.findAll()).thenReturn(List.of());
        stubNoSalesThisMonth();

        DashboardSummaryResponse summary = dashboardService.getSummary("admin-1", true);

        assertThat(summary.getTotalSaleClosedThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
