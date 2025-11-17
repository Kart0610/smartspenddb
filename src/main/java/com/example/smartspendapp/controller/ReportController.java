package com.example.smartspendapp.controller;

import com.example.smartspendapp.model.Expense;
import com.example.smartspendapp.model.User;
import com.example.smartspendapp.repository.ExpenseRepository;
import com.example.smartspendapp.repository.UserRepository;
import com.example.smartspendapp.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    public ReportController(ReportService reportService,
                            UserRepository userRepository,
                            ExpenseRepository expenseRepository) {
        this.reportService = reportService;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * Download Excel for full-history or given date range.
     * Examples:
     *   GET /api/reports/excel
     *   GET /api/reports/excel?from=2025-11-01&to=2025-11-30
     */
    @GetMapping(value = "/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<ByteArrayResource> downloadExcel(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = userRepository.findByEmail(principal.getUsername()).orElseThrow();

            // Convert LocalDate -> LocalDateTime bounds
            LocalDateTime startDateTime = (from == null) ? null : from.atStartOfDay();
            LocalDateTime endDateTime = (to == null) ? null : to.atTime(LocalTime.MAX);

            List<Expense> rows;
            if (startDateTime == null && endDateTime == null) {
                // no date filter
                rows = expenseRepository.findByUserOrderByDateAsc(user);
            } else {
                // If either bound is null, replace with extreme values so repository between works
                LocalDateTime start = (startDateTime == null) ? LocalDateTime.MIN : startDateTime;
                LocalDateTime end = (endDateTime == null) ? LocalDateTime.MAX : endDateTime;

                rows = expenseRepository.findByUserAndDateBetweenOrderByDateAsc(
    user,
    startDateTime.toLocalDate(),
    endDateTime.toLocalDate()
);

            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            reportService.generateExcelReport(rows, bos);
            byte[] bytes = bos.toByteArray();

            if (bytes == null || bytes.length == 0) {
                log.error("Excel generation returned empty bytes");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            ByteArrayResource resource = new ByteArrayResource(bytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename("expenses-report.xlsx").build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(bytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to generate Excel report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download PDF for full-history or given date range.
     * Examples:
     *   GET /api/reports/pdf
     *   GET /api/reports/pdf?from=2025-11-01&to=2025-11-30
     */
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> exportPdf(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = userRepository.findByEmail(principal.getUsername()).orElseThrow();

            // Convert LocalDate -> LocalDateTime bounds
            LocalDateTime startDateTime = (from == null) ? null : from.atStartOfDay();
            LocalDateTime endDateTime = (to == null) ? null : to.atTime(LocalTime.MAX);

            List<Expense> rows;
            if (startDateTime == null && endDateTime == null) {
                rows = expenseRepository.findByUserOrderByDateAsc(user);
            } else {
                LocalDateTime start = (startDateTime == null) ? LocalDateTime.MIN : startDateTime;
                LocalDateTime end = (endDateTime == null) ? LocalDateTime.MAX : endDateTime;
                rows = expenseRepository.findByUserAndDateBetweenOrderByDateAsc(
    user,
    startDateTime.toLocalDate(),
    endDateTime.toLocalDate()
);

            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            reportService.generatePdfReport(rows, bos);
            byte[] pdfBytes = bos.toByteArray();

            if (pdfBytes == null || pdfBytes.length == 0) {
                log.error("PDF generation returned empty bytes");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            ByteArrayResource resource = new ByteArrayResource(pdfBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename("expenses-report.pdf").build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(pdfBytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to generate PDF report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
