package com.example.insurancecrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReassignAllRequest {

    @NotBlank
    private String fromAgentId;

    @NotBlank
    private String toAgentId;
}
