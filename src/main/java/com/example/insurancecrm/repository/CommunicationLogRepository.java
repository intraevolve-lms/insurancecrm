package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.CommunicationLog;
import com.example.insurancecrm.enums.CommunicationOutcome;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunicationLogRepository extends MongoRepository<CommunicationLog, String> {

    List<CommunicationLog> findByCustomerIdOrderByLoggedAtDesc(String customerId);

    Optional<CommunicationLog> findFirstByLoggedByOrderByLoggedAtDesc(String loggedBy);

    List<CommunicationLog> findByOutcomeAndLoggedAtBetween(
            CommunicationOutcome outcome, LocalDateTime from, LocalDateTime to);

    List<CommunicationLog> findByOutcomeAndLoggedByAndLoggedAtBetween(
            CommunicationOutcome outcome, String loggedBy, LocalDateTime from, LocalDateTime to);

    void deleteByCustomerId(String customerId);

    void deleteByCustomerIdIn(List<String> customerIds);
}
