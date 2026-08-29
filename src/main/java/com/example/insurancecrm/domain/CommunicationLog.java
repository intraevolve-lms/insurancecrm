package com.example.insurancecrm.domain;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "communication_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunicationLog {

    @Id
    private String id;

    @Indexed
    private String customerId;

    private CommunicationChannel channel;

    private CommunicationOutcome outcome;

    private String notes;

    private LocalDateTime followUpDate;

    // Sale Close details — required (see CommunicationLogService) when outcome is SALE_CLOSE,
    // unused otherwise. Premium is captured here (separate from Customer.lastYearPremium) so it
    // reflects the specific policy just sold and can be summed for the monthly sales total.
    private BigDecimal premium;
    private String companyName;
    private String planName;
    private String scheme;
    private String city;
    private String portabilityOrFresh;
    private String tenure;

    private String loggedBy;      // userId
    private String loggedByName;

    private LocalDateTime loggedAt;
}
