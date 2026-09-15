package com.example.insurancecrm.dto.request;

import com.example.insurancecrm.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    // Deliberately unconstrained, unlike CreateUserRequest.password — blank/omitted means
    // "keep the current password unchanged" (see UserService.updateUser).
    private String password;

    @NotNull
    private Role role;
}
