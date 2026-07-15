package com.example.insurancecrm.domain;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "communication_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunicationLog {

    @Id
    private String id;

    // Either customerId or leadId is set, not both
    @Indexed
    private String customerId;

    @Indexed
    private String leadId;

    private CommunicationChannel channel;

    private CommunicationOutcome outcome;

    private String notes;

    private LocalDateTime followUpDate;

    private String loggedBy;      // userId
    private String loggedByName;

    private LocalDateTime loggedAt;
}
