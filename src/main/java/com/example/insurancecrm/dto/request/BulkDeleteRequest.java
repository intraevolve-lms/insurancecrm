package com.example.insurancecrm.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkDeleteRequest {

    @NotEmpty(message = "ids must not be empty")
    private List<String> ids;
}
