package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.CommunicationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CommunicationLogRepository extends MongoRepository<CommunicationLog, String> {

    List<CommunicationLog> findByCustomerIdOrderByLoggedAtDesc(String customerId);

    Optional<CommunicationLog> findFirstByLoggedByOrderByLoggedAtDesc(String loggedBy);

    void deleteByCustomerId(String customerId);

    void deleteByCustomerIdIn(List<String> customerIds);
}
