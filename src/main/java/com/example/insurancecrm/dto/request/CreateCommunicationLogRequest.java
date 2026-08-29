package com.example.insurancecrm.dto.request;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateCommunicationLogRequest {

    @NotNull
    private CommunicationChannel channel;

    @NotNull
    private CommunicationOutcome outcome;

    private String notes;

    private LocalDateTime followUpDate;

    // Sale Close details — CommunicationLogService requires all of these when outcome is
    // SALE_CLOSE. Left null/blank for every other outcome.
    private BigDecimal premium;
    private String companyName;
    private String planName;
    private String scheme;
    private String city;
    private String portabilityOrFresh;
    private String tenure;
}
