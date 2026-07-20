package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentPerformanceResponse {
    private String agentId;
    private String agentName;
    private long totalCustomers;
    private long myCallback;
    private long callback;
    private long prospect;
    private long ringing;
    private long switchOff;
    private long hangUp;
    private long nextYear;
    private long languageIssue;
    private LocalDateTime lastActivityAt;
}
