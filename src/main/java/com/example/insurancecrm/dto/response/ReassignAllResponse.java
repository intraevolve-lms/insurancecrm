package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReassignAllResponse {
    private String fromAgentId;
    private String toAgentId;
    private String toAgentName;
    private long reassignedCount;
}
