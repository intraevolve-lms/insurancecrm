package com.example.insurancecrm.config;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("No users exist and ADMIN_EMAIL/ADMIN_PASSWORD are not set — no admin account was " +
                    "created. Set both environment variables and restart, or insert an admin document " +
                    "directly into the 'users' collection.");
            return;
        }

        if (adminPassword.length() < 8) {
            log.warn("ADMIN_PASSWORD is shorter than 8 characters — refusing to seed an admin account " +
                    "with a weak password. Set a stronger ADMIN_PASSWORD and restart.");
            return;
        }

        User admin = User.builder()
                .name("Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .active(true)
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(admin);
        log.info("Initial admin account created: {} (must change password on first login)", adminEmail);
    }
}
