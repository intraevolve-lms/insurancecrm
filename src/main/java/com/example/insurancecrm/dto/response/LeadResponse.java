package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.PolicyType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LeadResponse {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private LeadSource source;
    private LeadStatus status;
    private PolicyType interestedIn;
    private String notes;
    private BigDecimal estimatedPremium;
    private String assignedAgentId;
    private String assignedAgentName;
    private LocalDateTime followUpDate;
    private LocalDateTime lastContactedAt;
    private CommunicationOutcome lastOutcome;
    private String convertedCustomerId;
    private LocalDateTime convertedAt;
    private String lostReason;
    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
