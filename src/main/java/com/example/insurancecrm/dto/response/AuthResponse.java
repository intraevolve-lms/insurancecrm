package com.example.insurancecrm.dto.response;

import com.example.insurancecrm.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String userId;
    private String name;
    private String email;
    private Role role;
    private boolean mustChangePassword;
}
