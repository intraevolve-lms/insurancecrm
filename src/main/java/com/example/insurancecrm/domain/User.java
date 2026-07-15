package com.example.insurancecrm.domain;

import com.example.insurancecrm.enums.Role;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String password;

    private Role role;

    private boolean active;

    private boolean mustChangePassword;

    private LocalDateTime createdAt;

    /** Tokens issued before this instant are rejected — set by an admin force-logout. Null means no forced logout has ever happened. */
    private LocalDateTime tokensInvalidBefore;

    /** Updated on every authenticated request/login/refresh for AGENT users; used to auto-logout idle agents. Null means no activity recorded yet. */
    private LocalDateTime lastActivityAt;
}
