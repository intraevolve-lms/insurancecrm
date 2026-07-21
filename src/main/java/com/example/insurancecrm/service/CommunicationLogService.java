package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateCommunicationLogRequest;
import com.example.insurancecrm.dto.response.CommunicationLogResponse;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.CommunicationLogRepository;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.AccessControl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunicationLogService {

    private final CommunicationLogRepository logRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public List<CommunicationLogResponse> getByCustomer(String customerId, String currentUserId, boolean isAdmin) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        AccessControl.requireOwnerOrAdmin(customer.getAssignedAgentId(), currentUserId, isAdmin);
        return logRepository.findByCustomerIdOrderByLoggedAtDesc(customerId)
                .stream().map(this::toResponse).toList();
    }

    public CommunicationLogResponse logForCustomer(String customerId,
                                                    CreateCommunicationLogRequest req,
                                                    String userId, boolean isAdmin) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        AccessControl.requireOwnerOrAdmin(customer.getAssignedAgentId(), userId, isAdmin);

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + userId));

        CommunicationLog log = CommunicationLog.builder()
                .customerId(customerId)
                .channel(req.getChannel())
                .outcome(req.getOutcome())
                .notes(req.getNotes())
                .followUpDate(req.getFollowUpDate())
                .loggedBy(userId)
                .loggedByName(agent.getName())
                .loggedAt(LocalDateTime.now())
                .build();

        CommunicationLogResponse response = toResponse(logRepository.save(log));

        customer.setLastOutcome(req.getOutcome());
        customer.setUpdatedAt(LocalDateTime.now());
        customerRepository.save(customer);

        return response;
    }

    public void delete(String id, String requesterId, boolean isAdmin) {
        CommunicationLog log = logRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Log not found: " + id));
        if (!isAdmin && !log.getLoggedBy().equals(requesterId)) {
            throw ApiException.forbidden("You can only delete your own logs");
        }
        logRepository.delete(log);
    }

    private CommunicationLogResponse toResponse(CommunicationLog l) {
        return CommunicationLogResponse.builder()
                .id(l.getId())
                .customerId(l.getCustomerId())
                .channel(l.getChannel())
                .outcome(l.getOutcome())
                .notes(l.getNotes())
                .followUpDate(l.getFollowUpDate())
                .loggedBy(l.getLoggedBy())
                .loggedByName(l.getLoggedByName())
                .loggedAt(l.getLoggedAt())
                .build();
    }
}
