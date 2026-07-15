package com.example.insurancecrm.dto.request;

import com.example.insurancecrm.enums.LeadSource;
import com.example.insurancecrm.enums.PolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateLeadRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    private String email;

    private String address;

    @NotNull
    private LeadSource source;

    private PolicyType interestedIn;

    private String notes;

    private BigDecimal estimatedPremium;

    private String assignedAgentId;

    private LocalDateTime followUpDate;
}
