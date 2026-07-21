package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReminderResponse {

    public enum ReminderType { COMMUNICATION_FOLLOWUP }

    public enum EntityKind { CUSTOMER }

    private String id;
    private ReminderType type;
    private String entityId;
    private EntityKind entityKind;
    private String entityName;
    private String description;
    private LocalDateTime dueDate;
    private long overdueDays;    // 0 = due today, >0 = overdue
    private String assignedToId;
    private String assignedToName;
}
