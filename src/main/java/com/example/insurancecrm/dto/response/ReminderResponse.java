package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReminderResponse {

    public enum ReminderType { LEAD_FOLLOWUP, COMMUNICATION_FOLLOWUP }

    /** What entityId refers to — COMMUNICATION_FOLLOWUP reminders can be logged against either. */
    public enum EntityKind { CUSTOMER, LEAD }

    private String id;
    private ReminderType type;
    private String entityId;
    private EntityKind entityKind;
    private String entityName;   // customer or lead name
    private String description;
    private LocalDateTime dueDate;
    private long overdueDays;    // 0 = due today, >0 = overdue
    private String assignedToId;
    private String assignedToName;
}
