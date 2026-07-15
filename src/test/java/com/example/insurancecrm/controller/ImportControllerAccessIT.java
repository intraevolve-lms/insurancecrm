package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the @PreAuthorize("hasRole('ADMIN')") gate on ImportController — per client feedback,
 * agents must not be able to bulk-import customer data via Excel/CSV; only admins can.
 *
 * MockMvc is built manually (rather than via @AutoConfigureMockMvc) because this Spring Boot
 * version doesn't bundle spring-boot-test-autoconfigure's web/servlet test slice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ImportControllerAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "ic-admin@test.com";
    private static final String AGENT_EMAIL = "ic-agent@test.com";

    private String adminToken;
    private String agentToken;

    private final MockMultipartFile csvFile = new MockMultipartFile(
            "file", "customers.csv", "text/csv",
            "Name,Plan,Last Year Premium,Expiry Date,Email,DOB,Phone,Address,Notes\nJohn Doe,,,,,,9000000001,,\n"
                    .getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        User agent = userRepository.save(User.builder().name("Agent").email(AGENT_EMAIL)
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
        customerRepository.searchByNameOrPhone("9000000001").forEach(customerRepository::delete);
    }

    @Test
    void importCustomers_agent_isForbidden() throws Exception {
        mockMvc.perform(multipart("/api/import/customers").file(csvFile)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void importCustomers_admin_isAllowed() throws Exception {
        mockMvc.perform(multipart("/api/import/customers").file(csvFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void importCustomers_noToken_returns401NotForbidden() throws Exception {
        mockMvc.perform(multipart("/api/import/customers").file(csvFile))
                .andExpect(status().isUnauthorized());
    }
}
