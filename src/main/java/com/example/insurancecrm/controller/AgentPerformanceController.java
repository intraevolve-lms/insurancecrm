package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.response.AgentPerformanceResponse;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.AgentPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-performance")
@RequiredArgsConstructor
@Tag(name = "Agent Performance", description = "Per-agent call-outcome funnel stats, sourced from the Log Activity feature on Customers. " +
        "Admins see every active agent; agents see only their own row.")
public class AgentPerformanceController {

    private final AgentPerformanceService agentPerformanceService;
    private final UserRepository userRepository;

    @Operation(summary = "Get agent performance stats",
        description = "For each agent: total assigned customers, counts broken down by last logged call outcome " +
                      "(My Callback, Callback, Prospect, Ringing, Switch Off, Hang Up, Next Year), and the timestamp of their most recent logged activity.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Performance stats returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentPerformanceResponse>>> getPerformance(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(agentPerformanceService.getPerformance(getUserId(auth), isAdmin(auth))));
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
