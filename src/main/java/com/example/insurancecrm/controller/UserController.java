package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.request.CreateUserRequest;
import com.example.insurancecrm.dto.request.ForceLogoutRequest;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.UserResponse;
import com.example.insurancecrm.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Admin-only endpoints to create and manage agent accounts. Only users with the ADMIN role can access these endpoints.")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List all users", description = "Returns every user (admin and agent) registered in the system.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User list returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers()));
    }

    @Operation(summary = "Get user by ID", description = "Fetch the full profile of a single user by their MongoDB ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(
            @Parameter(description = "MongoDB ID of the user", required = true) @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @Operation(summary = "Create a new user", description = "Create a new ADMIN or AGENT account. The email must be unique. " +
            "Agents can only see their own assigned customers and tasks.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(userService.createUser(request)));
    }

    @Operation(summary = "Update a user", description = "Update name, email, role, or password. Omit password to keep it unchanged.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already used by another account", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @Parameter(description = "MongoDB ID of the user to update", required = true) @PathVariable String id,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(id, request)));
    }

    @Operation(summary = "Deactivate a user", description = "Soft-deletes a user — they can no longer log in but their historical data is preserved.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deactivated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @Parameter(description = "MongoDB ID of the user to deactivate", required = true) @PathVariable String id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.noContent("User deactivated successfully"));
    }

    @Operation(summary = "Force logout agents", description = "Immediately ends the active sessions of the given agent accounts — " +
            "their access and refresh tokens stop working right away, even mid-session, and they'll be redirected to the login " +
            "page on their next request. Admin accounts in the list are ignored.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Selected agents logged out"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No matching agent accounts found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @PostMapping("/force-logout")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@Valid @RequestBody ForceLogoutRequest request) {
        userService.forceLogout(request.getUserIds());
        return ResponseEntity.ok(ApiResponse.noContent("Selected agents have been logged out"));
    }
}
