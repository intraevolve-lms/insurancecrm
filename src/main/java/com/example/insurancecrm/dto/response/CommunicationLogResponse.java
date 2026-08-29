package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CommunicationLogResponse {
    private String id;
    private String customerId;
    private CommunicationChannel channel;
    private CommunicationOutcome outcome;
    private String notes;
    private LocalDateTime followUpDate;
    private BigDecimal premium;
    private String companyName;
    private String planName;
    private String scheme;
    private String city;
    private String portabilityOrFresh;
    private String tenure;
    private String loggedBy;
    private String loggedByName;
    private LocalDateTime loggedAt;
}
