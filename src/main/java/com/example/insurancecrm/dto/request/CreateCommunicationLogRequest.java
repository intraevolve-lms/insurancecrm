package com.example.insurancecrm.dto.request;

import com.example.insurancecrm.enums.CommunicationChannel;
import com.example.insurancecrm.enums.CommunicationOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateCommunicationLogRequest {

    @NotNull
    private CommunicationChannel channel;

    @NotNull
    private CommunicationOutcome outcome;

    private String notes;

    private LocalDateTime followUpDate;
}
