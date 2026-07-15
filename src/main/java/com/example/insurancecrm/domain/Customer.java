package com.example.insurancecrm.domain;

import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    private String id;

    private String name;

    @Indexed
    private String phone;

    private String email;

    private String address;

    private String notes;

    private LocalDate dateOfBirth;

    // Plan/premium/expiry captured as flat fields on the customer record — there is no
    // separate Policy entity in this app.
    private String plan;

    private BigDecimal lastYearPremium;

    private LocalDate expiryDate;

    @Indexed
    private String assignedAgentId;

    // Most recent outcome logged via Log Activity — drives the Agent Performance call-outcome funnel
    @Indexed
    private CommunicationOutcome lastOutcome;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
