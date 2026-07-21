package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.BulkAssignRequest;
import com.example.insurancecrm.dto.request.BulkDeleteRequest;
import com.example.insurancecrm.dto.request.CreateCustomerRequest;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.BulkAssignResponse;
import com.example.insurancecrm.dto.response.BulkDeleteResponse;
import com.example.insurancecrm.dto.response.CustomerResponse;
import com.example.insurancecrm.dto.response.PagedResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.CustomerService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "Create and manage policyholders. Admins see all customers; agents see only their assigned customers.")
public class CustomerController {

    private final CustomerService customerService;
    private final UserRepository userRepository;

    @Operation(summary = "List customers", description = "Admins receive all customers; agents receive only their assigned customers. " +
            "Results are paginated (default 20 per page) and sorted newest-first unless sortBy is given.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer page returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: 'premium' or 'expiryDate'. Omit for newest-first.") @RequestParam(required = false) String sortBy,
            @Parameter(description = "'asc' or 'desc', defaults to 'desc'") @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) CommunicationOutcome outcome,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.getAllCustomers(getUserId(auth), isAdmin(auth), page, size, sortBy, sortDir, outcome)));
    }

    @Operation(summary = "Search customers", description = "Case-insensitive search across name and phone number. Paginated like the main list.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Matching customer page returned")
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> search(
            @Parameter(description = "Search term matched against name and phone", required = true, example = "Rahul")
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) CommunicationOutcome outcome,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.search(q, getUserId(auth), isAdmin(auth), page, size, sortBy, sortDir, outcome)));
    }

    @Operation(summary = "List new (uncontacted) customers", description = "Customers with no communication outcome logged yet — " +
            "a focused work queue so a freshly assigned customer doesn't sit invisible in the middle of the full list. " +
            "Admins see every uncontacted customer; agents see only their own. Oldest-assigned first. " +
            "A customer drops off this list the moment any activity is logged against them.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Uncontacted customer page returned")
    })
    @GetMapping("/new")
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> getNew(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Search term matched against name and phone") @RequestParam(required = false) String q,
            @Parameter(description = "Sort field: 'premium' or 'expiryDate'. Omit for oldest-assigned-first.") @RequestParam(required = false) String sortBy,
            @Parameter(description = "'asc' or 'desc'") @RequestParam(required = false) String sortDir,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.getNewCustomers(getUserId(auth), isAdmin(auth), page, size, q, sortBy, sortDir)));
    }

    @Operation(summary = "Get customer by ID", description = "Returns the full profile including assigned agent details. " +
            "Also records that the current user opened this record, and reports who last opened it before them.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(
            @Parameter(description = "MongoDB ID of the customer", required = true) @PathVariable String id,
            Authentication auth) {
        User viewer = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> ApiException.notFound("User not found: " + auth.getName()));
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.getByIdForView(id, viewer.getId(), viewer.getName(), isAdmin(auth))));
    }

    @Operation(summary = "Create a customer", description = "Name and phone are required. Optionally assign to an agent at creation time.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Customer created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(customerService.create(request)));
    }

    @Operation(summary = "Update a customer", description = "Admin-only. Update any field on the customer record.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @Parameter(description = "MongoDB ID of the customer", required = true) @PathVariable String id,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request)));
    }

    @Operation(summary = "Assign customer to an agent", description = "Admin-only. Assigns or re-assigns this customer to a specific agent.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer assigned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer or agent not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @PatchMapping("/{id}/assign/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> assignAgent(
            @Parameter(description = "MongoDB ID of the customer", required = true) @PathVariable String id,
            @Parameter(description = "MongoDB ID of the agent to assign", required = true) @PathVariable String agentId) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.assignAgent(id, agentId)));
    }

    @Operation(summary = "Bulk-assign customers to an agent", description = "Admin-only. Assigns every given customer to the specified agent in one request. Customer IDs that don't exist are skipped and reported rather than failing the whole request.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customers assigned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Agent not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    @PatchMapping("/bulk-assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkAssignResponse>> bulkAssignAgent(@Valid @RequestBody BulkAssignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerService.bulkAssignAgent(request.getCustomerIds(), request.getAgentId())));
    }

    @Operation(summary = "Bulk-delete customers", description = "Admin-only. Permanently deletes every given customer in one request. Customer IDs that don't exist are skipped and reported rather than failing the whole request.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customers deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkDeleteResponse>> bulkDelete(@Valid @RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.bulkDelete(request.getIds())));
    }

    @Operation(summary = "Delete a customer", description = "Admin-only. Permanently removes the customer record.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Customer deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Customer not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin role required", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "MongoDB ID of the customer", required = true) @PathVariable String id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.noContent("Customer deleted successfully"));
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
