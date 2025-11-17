package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.ExpenseRepository;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportController (direct controller invocation, Mockito mocks).
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ReportController reportController;

    private final String email = "tester@example.com";
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(email);
    }

    @Test
    void downloadExcel_fullHistory_returnsOkWithExcelBytes() throws Exception {
        // Arrange
        Expense e1 = new Expense();
        Expense e2 = new Expense();
        List<Expense> rows = List.of(e1, e2);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserOrderByDateAsc(testUser)).thenReturn(rows);

        // make reportService write bytes into the provided ByteArrayOutputStream
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ByteArrayOutputStream bos = (ByteArrayOutputStream) args[1];
            byte[] sample = "excel-bytes".getBytes();
            bos.write(sample);
            return null;
        }).when(reportService).generateExcelReport(eq(rows), any(ByteArrayOutputStream.class));

        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);

        // Act
        ResponseEntity<ByteArrayResource> resp = reportController.downloadExcel(principal, null, null);

        // Assert
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        ByteArrayResource body = resp.getBody();
        assertThat(body.contentLength()).isGreaterThan(0);
        assertThat(resp.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(resp.getHeaders().getFirst("Content-Disposition"))
                .contains("filename=\"expenses-report.xlsx\"");

        verify(userRepository).findByEmail(email);
        verify(expenseRepository).findByUserOrderByDateAsc(testUser);
        verify(reportService).generateExcelReport(eq(rows), any(ByteArrayOutputStream.class));
    }

    @Test
    void downloadExcel_dateRange_callsDateBetweenRepoAndReturnsOk() throws Exception {
        // Arrange
        LocalDate from = LocalDate.of(2025, 11, 1);
        LocalDate to = LocalDate.of(2025, 11, 30);

        Expense e = new Expense();
        List<Expense> rows = List.of(e);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndDateBetweenOrderByDateAsc(eq(testUser), eq(from), eq(to)))
                .thenReturn(rows);

        doAnswer(invocation -> {
            ByteArrayOutputStream bos = (ByteArrayOutputStream) invocation.getArguments()[1];
            bos.write("excel-range".getBytes());
            return null;
        }).when(reportService).generateExcelReport(eq(rows), any(ByteArrayOutputStream.class));

        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);

        // Act
        ResponseEntity<ByteArrayResource> resp = reportController.downloadExcel(principal, from, to);

        // Assert
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody().contentLength()).isGreaterThan(0);
        assertThat(resp.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(resp.getHeaders().getFirst("Content-Disposition"))
                .contains("filename=\"expenses-report.xlsx\"");

        verify(userRepository).findByEmail(email);
        verify(expenseRepository).findByUserAndDateBetweenOrderByDateAsc(testUser, from, to);
        verify(reportService).generateExcelReport(eq(rows), any(ByteArrayOutputStream.class));
    }

    @Test
    void exportPdf_fullHistory_returnsPdfBytes() throws Exception {
        // Arrange
        Expense e1 = new Expense();
        List<Expense> rows = List.of(e1);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserOrderByDateAsc(testUser)).thenReturn(rows);

        doAnswer(invocation -> {
            ByteArrayOutputStream bos = (ByteArrayOutputStream) invocation.getArguments()[1];
            bos.write("pdf-bytes".getBytes());
            return null;
        }).when(reportService).generatePdfReport(eq(rows), any(ByteArrayOutputStream.class));

        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);

        // Act
        ResponseEntity<ByteArrayResource> resp = reportController.exportPdf(principal, null, null);

        // Assert
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().contentLength()).isGreaterThan(0);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
        assertThat(resp.getHeaders().getFirst("Content-Disposition"))
                .contains("filename=\"expenses-report.pdf\"");

        verify(userRepository).findByEmail(email);
        verify(expenseRepository).findByUserOrderByDateAsc(testUser);
        verify(reportService).generatePdfReport(eq(rows), any(ByteArrayOutputStream.class));
    }

    @Test
    void downloadExcel_unauthorized_whenPrincipalNull_returns401() {
        // Act
        ResponseEntity<ByteArrayResource> resp = reportController.downloadExcel(null, null, null);

        // Assert
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(reportService, userRepository, expenseRepository);
    }

    @Test
    void exportPdf_emptyBytesFromService_returns500() throws Exception {
        // Arrange: service does nothing -> bytes empty -> controller should return 500
        List<Expense> rows = List.of();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserOrderByDateAsc(testUser)).thenReturn(rows);

        // reportService.generatePdfReport will not write to stream (default mock does nothing)

        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(email);

        // Act
        ResponseEntity<ByteArrayResource> resp = reportController.exportPdf(principal, null, null);

        // Assert
        assertThat(resp.getStatusCodeValue()).isEqualTo(500);

        verify(userRepository).findByEmail(email);
        verify(expenseRepository).findByUserOrderByDateAsc(testUser);
        verify(reportService).generatePdfReport(eq(rows), any(ByteArrayOutputStream.class));
    }
}

