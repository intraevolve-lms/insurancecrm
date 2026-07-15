package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadStatus;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Full-dataset counts for the pipeline/outcome summary tiles, independent of the current page or filter. */
@Data
@Builder
public class LeadSummaryResponse {
    private Map<LeadStatus, Long> statusCounts;
    private Map<CommunicationOutcome, Long> outcomeCounts;
}
