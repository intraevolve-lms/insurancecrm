package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.ReminderResponse;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
@Tag(name = "Reminders", description = "Returns all pending follow-ups due today or overdue — from communication log follow-up dates.")
public class ReminderController {

    private final ReminderService reminderService;
    private final UserRepository userRepository;

    @Operation(summary = "Get all pending reminders",
               description = "Aggregates overdue and due-today follow-ups from communication logs. Sorted by most overdue first.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReminderResponse>>> getReminders(Authentication auth) {
        String userId = userRepository.findByEmail(auth.getName())
                .map(u -> u.getId()).orElse(null);
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.ok(reminderService.getReminders(userId, isAdmin)));
    }
}
