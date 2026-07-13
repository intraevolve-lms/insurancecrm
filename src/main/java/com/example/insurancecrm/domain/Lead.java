package com.example.insurancecrm.domain;

import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.enums.PolicyType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "leads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    private String id;

    private String name;

    @Indexed
    private String phone;

    private String email;

    private String address;

    private LeadSource source;

    @Indexed
    private LeadStatus status;

    private PolicyType interestedIn;

    private String notes;

    private BigDecimal estimatedPremium;

    @Indexed
    private String assignedAgentId;

    private LocalDateTime followUpDate;

    private LocalDateTime lastContactedAt;

    // Most recent outcome logged via Log Activity — drives the dashboard call-outcome funnel
    @Indexed
    private CommunicationOutcome lastOutcome;

    // Set when status = CONVERTED
    private String convertedCustomerId;
    private LocalDateTime convertedAt;

    // Set when status = LOST
    private String lostReason;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
