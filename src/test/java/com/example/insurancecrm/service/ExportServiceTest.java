package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.Customer;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.CustomerRepository;
import com.example.insurancecrm.repository.UserRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExportService exportService;

    private final Customer c1 = Customer.builder().id("c1").name("Customer One").phone("9111111111")
            .assignedAgentId("agent-1").build();
    private final Customer c2 = Customer.builder().id("c2").name("Customer Two").phone("9222222222")
            .assignedAgentId("agent-2").build();

    @Test
    void exportCustomers_noFilter_exportsEveryCustomer() throws Exception {
        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));
        when(userRepository.findAll()).thenReturn(List.of());

        byte[] bytes = exportService.exportCustomers(null);

        assertThat(rowCount(bytes)).isEqualTo(2);
        verify(customerRepository, never()).findByAssignedAgentId(any());
    }

    @Test
    void exportCustomers_withAgentFilter_exportsOnlyThatAgentsCustomers() throws Exception {
        when(customerRepository.findByAssignedAgentId("agent-1")).thenReturn(List.of(c1));
        when(userRepository.findAll()).thenReturn(List.of());

        byte[] bytes = exportService.exportCustomers("agent-1");

        assertThat(rowCount(bytes)).isEqualTo(1);
        assertThat(firstDataRow(bytes)[1]).isEqualTo("Customer One");
    }

    @Test
    void exportCustomers_resolvesAssignedAgentNameInOutput() throws Exception {
        User agent = User.builder().id("agent-1").name("Agent One").role(Role.AGENT).build();
        when(customerRepository.findAll()).thenReturn(List.of(c1));
        when(userRepository.findAll()).thenReturn(List.of(agent));

        byte[] bytes = exportService.exportCustomers(null);

        assertThat(firstDataRow(bytes)[10]).isEqualTo("Agent One");
    }

    @Test
    void exportCustomers_unassignedCustomer_showsEmDash() throws Exception {
        Customer unassigned = Customer.builder().id("c3").name("Unassigned Cust").phone("9333333333").build();
        when(customerRepository.findAll()).thenReturn(List.of(unassigned));
        when(userRepository.findAll()).thenReturn(List.of());

        byte[] bytes = exportService.exportCustomers(null);

        assertThat(firstDataRow(bytes)[10]).isEqualTo("—");
    }

    @Test
    void exportCustomers_writesExpectedHeaderRow() throws Exception {
        when(customerRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of());

        byte[] bytes = exportService.exportCustomers(null);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            var header = sheet.getRow(0);
            assertThat(fmt.formatCellValue(header.getCell(1))).isEqualTo("Name");
            assertThat(fmt.formatCellValue(header.getCell(2))).isEqualTo("Phone");
            assertThat(fmt.formatCellValue(header.getCell(10))).isEqualTo("Assigned Agent");
        }
    }

    private int rowCount(byte[] bytes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            return wb.getSheetAt(0).getLastRowNum(); // header is row 0, so lastRowNum == data row count
        }
    }

    private String[] firstDataRow(byte[] bytes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            var row = sheet.getRow(1);
            String[] values = new String[12];
            for (int i = 0; i < 12; i++) values[i] = fmt.formatCellValue(row.getCell(i));
            return values;
        }
    }
}
