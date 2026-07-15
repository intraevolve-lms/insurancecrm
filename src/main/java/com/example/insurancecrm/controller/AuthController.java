package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.request.ChangePasswordRequest;
import com.example.insurancecrm.dto.request.LoginRequest;
import com.example.insurancecrm.dto.request.RefreshTokenRequest;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.AuthResponse;
import com.example.insurancecrm.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login endpoint. Returns a JWT token that must be passed as a Bearer token in the Authorization header for all other API calls.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Login", description = "Authenticate with email and password. Returns a short-lived access token " +
            "(1 hour) plus a longer-lived refresh token (30 days). Use the refresh token via POST /api/auth/refresh " +
            "to silently obtain a new access token without asking the user to log in again. " +
            "Copy the access token value and click the 'Authorize' button at the top of this page to use secured endpoints.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful — tokens in data field"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or malformed request body", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @Operation(summary = "Refresh access token", description = "Exchanges a valid, non-expired refresh token for a new access token. " +
            "Called automatically by the frontend when an API call fails with 401 — not meant to be called manually.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New access token issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Refresh token invalid, expired, or account deactivated", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.getRefreshToken())));
    }

    @Operation(summary = "Change password", description = "Change the current (authenticated) admin's own password. " +
            "Requires the current password. Also clears the 'must change password' flag set on a freshly-seeded " +
            "admin account, so this is how that first forced password change is completed. Agents cannot change " +
            "their own password this way — an admin must set it for them via PUT /api/users/{id}.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Current password incorrect, or new password too short", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required — agents must ask an admin to change their password", content = @Content)
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication authentication,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.noContent("Password changed successfully"));
    }
}
