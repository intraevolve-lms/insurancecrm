package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.Lead;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LeadRepository extends MongoRepository<Lead, String> {

    List<Lead> findByAssignedAgentId(String agentId);

    List<Lead> findByStatus(LeadStatus status);

    long countByStatus(LeadStatus status);

    long countByAssignedAgentIdAndStatus(String agentId, LeadStatus status);

    long countByLastOutcomeAndStatusNotIn(CommunicationOutcome outcome, List<LeadStatus> excludedStatuses);

    long countByAssignedAgentIdAndLastOutcomeAndStatusNotIn(
            String agentId, CommunicationOutcome outcome, List<LeadStatus> excludedStatuses);
}
