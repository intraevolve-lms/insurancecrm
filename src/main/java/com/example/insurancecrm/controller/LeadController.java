package com.example.insurancecrm.controller;

import com.example.insurancecrm.dto.request.BulkDeleteRequest;
import com.example.insurancecrm.dto.request.CreateLeadRequest;
import com.example.insurancecrm.dto.request.UpdateLeadStatusRequest;
import com.example.insurancecrm.dto.response.ApiResponse;
import com.example.insurancecrm.dto.response.BulkDeleteResponse;
import com.example.insurancecrm.dto.response.LeadResponse;
import com.example.insurancecrm.dto.response.LeadSummaryResponse;
import com.example.insurancecrm.dto.response.PagedResponse;
import com.example.insurancecrm.enums.CommunicationOutcome;
import com.example.insurancecrm.enums.LeadStatus;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Management", description = "Track prospective customers through the sales pipeline: New → Contacted → Quote Sent → Negotiating → Converted / Lost")
public class LeadController {

    private final LeadService leadService;
    private final UserRepository userRepository;

    @Operation(summary = "List leads", description = "Admins see all leads; agents see only their assigned leads. " +
            "Results are paginated (default 20 per page), newest first. Optionally filter by pipeline status, " +
            "last outcome (excludes Converted/Lost, matching the outcome funnel tiles), and a name/phone search term.")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<LeadResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) CommunicationOutcome outcome,
            @Parameter(description = "Search term matched against name and phone") @RequestParam(required = false) String q,
            Authentication auth) {
        String userId = getUserId(auth);
        boolean isAdmin = isAdmin(auth);
        return ResponseEntity.ok(ApiResponse.ok(leadService.getAll(userId, isAdmin, page, size, status, outcome, q)));
    }

    @Operation(summary = "Pipeline and outcome summary counts", description = "Full-dataset counts for the pipeline " +
            "status tiles and outcome funnel tiles, independent of the current page or filter on the main list.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<LeadSummaryResponse>> getSummary(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(leadService.getSummary(getUserId(auth), isAdmin(auth))));
    }

    @Operation(summary = "Get lead by ID", description = "Agents can only access their own assigned leads; admins can access any.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeadResponse>> getById(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(leadService.getById(id, getUserId(auth), isAdmin(auth))));
    }

    @Operation(summary = "Create a lead", description = "Register a new prospect. Status defaults to NEW.")
    @PostMapping
    public ResponseEntity<ApiResponse<LeadResponse>> create(
            @Valid @RequestBody CreateLeadRequest request, Authentication auth) {
        String userId = getUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(leadService.create(request, userId, isAdmin(auth))));
    }

    @Operation(summary = "Update a lead", description = "Agents can only update their own assigned leads; admins can update any.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LeadResponse>> update(
            @PathVariable String id, @Valid @RequestBody CreateLeadRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(leadService.update(id, request, getUserId(auth), isAdmin(auth))));
    }

    @Operation(summary = "Update lead status",
        description = "Move the lead through the pipeline. Provide lostReason when status = LOST. " +
                      "Agents can only update their own assigned leads; admins can update any.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<LeadResponse>> updateStatus(
            @PathVariable String id, @Valid @RequestBody UpdateLeadStatusRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(leadService.updateStatus(id, request, getUserId(auth), isAdmin(auth))));
    }

    @Operation(summary = "Convert lead to customer",
        description = "Creates a new Customer record from the lead data, marks the lead as CONVERTED, and links the two records. " +
                      "Agents can only convert their own assigned leads; admins can convert any.")
    @PostMapping("/{id}/convert")
    public ResponseEntity<ApiResponse<LeadResponse>> convert(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Lead converted to customer successfully",
                leadService.convertToCustomer(id, getUserId(auth), isAdmin(auth))));
    }

    @Operation(summary = "Delete a lead", description = "Admin only.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        leadService.delete(id);
        return ResponseEntity.ok(ApiResponse.noContent("Lead deleted"));
    }

    @Operation(summary = "Bulk-delete leads", description = "Admin-only. Permanently deletes every given lead in one request. Lead IDs that don't exist are skipped and reported rather than failing the whole request.")
    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkDeleteResponse>> bulkDelete(@Valid @RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(leadService.bulkDelete(request.getIds())));
    }

    private String getUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
