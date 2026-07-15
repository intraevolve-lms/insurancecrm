package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    // ─── Customers ────────────────────────────────────────────────
    /** Admin-only (enforced by @PreAuthorize on the controller) — agentId is an optional filter. */
    public byte[] exportCustomers(String agentId) throws Exception {
        List<Customer> customers = agentId != null
                ? customerRepository.findByAssignedAgentId(agentId)
                : customerRepository.findAll();

        Map<String, String> agentNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getName()));

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Customers");

            String[] headers = {"#", "Name", "Phone", "Email", "DOB", "Plan", "Last Year Premium", "Expiry Date",
                    "Address", "Notes", "Assigned Agent", "Created At"};
            writeHeader(wb, sheet, headers);

            int rowIdx = 1;
            for (Customer c : customers) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, rowIdx - 1);
                setCell(row, 1, c.getName());
                setCell(row, 2, c.getPhone());
                setCell(row, 3, nvl(c.getEmail()));
                setCell(row, 4, c.getDateOfBirth() != null ? c.getDateOfBirth().toString() : "");
                setCell(row, 5, nvl(c.getPlan()));
                if (c.getLastYearPremium() != null) row.createCell(6).setCellValue(c.getLastYearPremium().doubleValue());
                else row.createCell(6).setCellValue("");
                setCell(row, 7, c.getExpiryDate() != null ? c.getExpiryDate().toString() : "");
                setCell(row, 8, nvl(c.getAddress()));
                setCell(row, 9, nvl(c.getNotes()));
                setCell(row, 10, c.getAssignedAgentId() != null ? agentNames.getOrDefault(c.getAssignedAgentId(), c.getAssignedAgentId()) : "—");
                setCell(row, 11, c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : "");
            }

            autoSize(sheet, headers.length);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private void writeHeader(XSSFWorkbook wb, Sheet sheet, String[] headers) {
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        headerStyle.setFont(font);

        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        sheet.createFreezePane(0, 1);
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(width + 512, 15000));
        }
    }

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else cell.setCellValue(value != null ? value.toString() : "");
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
