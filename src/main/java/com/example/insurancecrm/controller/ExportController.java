package com.example.insurancecrm.controller;

import com.example.insurancecrm.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Data Export", description = "Admin-only. Download customers as Excel (.xlsx) files.")
public class ExportController {

    private final ExportService exportService;

    @Operation(summary = "Export customers to Excel",
               description = "Downloads an .xlsx file with contact details, plan/premium/expiry info, and assigned agent. " +
                             "Exports everyone by default, or a single agent's customers via the optional agentId filter.")
    @GetMapping("/customers")
    public ResponseEntity<byte[]> exportCustomers(
            @Parameter(description = "Optional filter — MongoDB ID of the agent to export customers for") @RequestParam(required = false) String agentId) throws Exception {
        byte[] data = exportService.exportCustomers(agentId);
        return xlsxResponse(data, "customers_" + LocalDate.now() + ".xlsx");
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}
