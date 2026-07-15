package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommunicationLogResponse {
    private String id;
    private String customerId;
    private String leadId;
    private CommunicationChannel channel;
    private CommunicationOutcome outcome;
    private String notes;
    private LocalDateTime followUpDate;
    private String loggedBy;
    private String loggedByName;
    private LocalDateTime loggedAt;
}
