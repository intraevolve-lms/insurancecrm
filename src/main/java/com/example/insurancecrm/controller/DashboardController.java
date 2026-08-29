package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.DashboardSummaryResponse;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.DashboardService;
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

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Home screen summary counts. First endpoint to call after login.")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    @Operation(summary = "Get dashboard summary",
        description = "Returns snapshot counts: totalCustomers, totalPolicies, expiring in 30/15/7/0 days, expired, pendingTasks, " +
                      "and totalSaleClosedThisMonth (sum of Premium across every Sale Close activity logged this calendar month). " +
                      "Admins see counts across everyone; agents see counts scoped to their own assigned customers / own logged activity. " +
                      "Use the Renewals endpoint to drill into the individual policies behind each expiry count.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Summary returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSummary(getUserId(auth), isAdmin(auth))));
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
