package com.example.insurancecrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkDeleteResponse {
    private int requestedCount;
    private int deletedCount;
    private List<String> notFoundIds;
}
