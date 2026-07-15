package com.example.insurancecrm.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ForceLogoutRequest {

    @NotEmpty
    private List<String> userIds;
}
