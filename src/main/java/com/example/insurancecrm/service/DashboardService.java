package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.dto.response.DashboardSummaryResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final CommunicationLogRepository communicationLogRepository;

    public DashboardSummaryResponse getSummary(String currentUserId, boolean isAdmin) {
        List<Customer> customers = isAdmin
                ? customerRepository.findAll()
                : customerRepository.findByAssignedAgentId(currentUserId);

        Map<CommunicationOutcome, Long> outcomeCounts = customers.stream()
                .map(Customer::getLastOutcome)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()));

        return DashboardSummaryResponse.builder()
                .totalCustomers(customers.size())
                .outcomeCounts(outcomeCounts)
                .totalSaleClosedThisMonth(sumSaleClosedThisMonth(currentUserId, isAdmin))
                .build();
    }

    // Sums Premium across every SALE_CLOSE log entered this calendar month — an event-based
    // total, not a snapshot of customers currently in SALE_CLOSE state (a customer's lastOutcome
    // can move on to something else later without undoing the sale that already happened).
    private BigDecimal sumSaleClosedThisMonth(String currentUserId, boolean isAdmin) {
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 59, 59);

        List<CommunicationLog> saleCloseLogs = isAdmin
                ? communicationLogRepository.findByOutcomeAndLoggedAtBetween(
                        CommunicationOutcome.SALE_CLOSE, monthStart, monthEnd)
                : communicationLogRepository.findByOutcomeAndLoggedByAndLoggedAtBetween(
                        CommunicationOutcome.SALE_CLOSE, currentUserId, monthStart, monthEnd);

        return saleCloseLogs.stream()
                .map(CommunicationLog::getPremium)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
