package com.example.smartspendapp.service;

import com.example.smartspendapp.model.Expense;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generate Excel (.xlsx) using streaming SXSSFWorkbook.
     * This method is defensive about nulls and different Expense field types.
     */
    public void generateExcelReport(List<Expense> expenses, OutputStream out) throws Exception {
        // ensure typed empty list to avoid raw-type compiler issues
        List<Expense> rows = (expenses == null) ? Collections.<Expense>emptyList() : expenses;

        SXSSFWorkbook workbook = new SXSSFWorkbook(100); // keep 100 rows in memory
        try {
            CreationHelper ch = workbook.getCreationHelper();

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle dateStyle = workbook.createCellStyle();
            short dateFmt = ch.createDataFormat().getFormat("yyyy-mm-dd");
            dateStyle.setDataFormat(dateFmt);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(ch.createDataFormat().getFormat("#,##0.00"));

            // Sheet
            Sheet sheet = workbook.createSheet("Expenses");

            // IMPORTANT for SXSSF: track columns before writing if you will call autoSizeColumn
            if (sheet instanceof SXSSFSheet) {
                ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            }

            // Header row
            String[] headers = {"Date", "Title", "Category", "Type", "Amount", "Description"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data rows
            int r = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (Expense e : rows) {
                Row row = sheet.createRow(r++);

                // Date: handle LocalDate, LocalDateTime, java.util.Date, String, null
                Cell dateCell = row.createCell(0);
                Object dateObj = safeGetDate(e); // helper below
                if (dateObj instanceof LocalDate) {
                    LocalDate ld = (LocalDate) dateObj;
                    dateCell.setCellValue(ld.format(DATE_FMT));
                    dateCell.setCellStyle(dateStyle);
                } else if (dateObj instanceof LocalDateTime) {
                    LocalDateTime ldt = (LocalDateTime) dateObj;
                    dateCell.setCellValue(ldt.format(DATE_TIME_FMT));
                    dateCell.setCellStyle(dateStyle);
                } else if (dateObj != null) {
                    dateCell.setCellValue(dateObj.toString());
                } else {
                    dateCell.setCellValue("");
                }

                // Title
                row.createCell(1).setCellValue(nullSafe(e.getTitle()));
                // Category
                row.createCell(2).setCellValue(nullSafe(e.getCategory()));
                // Type
                row.createCell(3).setCellValue(nullSafe(e.getType()));

                // Amount (prefer numeric cell if possible)
                Cell amountCell = row.createCell(4);
                Object amtObj = e.getAmount();
                Double numeric = parseNumberSafely(amtObj);
                if (numeric != null) {
                    amountCell.setCellValue(numeric);
                    amountCell.setCellStyle(moneyStyle);
                    total = total.add(BigDecimal.valueOf(numeric));
                } else {
                    amountCell.setCellValue(amtObj == null ? "" : amtObj.toString());
                }

                // Description
                row.createCell(5).setCellValue(nullSafe(e.getDescription()));
            }

            // Totals row
            Row totals = sheet.createRow(r + 1);
            totals.createCell(3).setCellValue("Total Expenses:");
            Cell totalCell = totals.createCell(4);
            totalCell.setCellValue(total.doubleValue());
            totalCell.setCellStyle(moneyStyle);

            // Auto-size columns (safe because we tracked earlier)
            for (int i = 0; i < headers.length; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception ignored) {
                    // sometimes autoSizeColumn fails in headless/streaming envs — ignore
                }
            }

            workbook.write(out);
            out.flush();
        } finally {
            // close and dispose temp files
            try { workbook.close(); } catch (Exception ignored) {}
            try { workbook.dispose(); } catch (Exception ignored) {}
        }
    }

    /**
     * Generate PDF using iText 7 layout API (keeps your existing iText-based PDF).
     */
    public void generatePdfReport(List<Expense> expenses, OutputStream out) throws Exception {
        List<Expense> rows = (expenses == null) ? Collections.<Expense>emptyList() : expenses;

        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, com.itextpdf.kernel.geom.PageSize.A4.rotate());
        document.setMargins(18, 18, 18, 18);

        // Title
        Paragraph title = new Paragraph("SmartSpend — Expenses Report")
                .setTextAlignment(TextAlignment.CENTER)
                .setBold()
                .setFontSize(14f);
        document.add(title);

        Paragraph meta = new Paragraph("Generated: " + java.time.LocalDate.now().toString())
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(9f);
        document.add(meta);

        // Table columns
        float[] columnWidths = {2f, 3f, 2f, 1.5f, 1.2f, 4f};
        Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth();

        // Header
        String[] headers = {"Date", "Title", "Category", "Type", "Amount", "Description"};
        for (String h : headers) {
            com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(h).setBold());
            headerCell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
            headerCell.setPadding(4f);
            table.addHeaderCell(headerCell);
        }

        double total = 0.0;
        for (Expense e : rows) {
            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(formatDateForPdf(e))));
            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullSafe(e.getTitle()))));
            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullSafe(e.getCategory()))));
            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullSafe(e.getType()))));

            Double val = parseNumberSafely(e.getAmount());
            String amountStr = val == null ? "" : String.format("%.2f", val);
            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(amountStr)));

            table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullSafe(e.getDescription()))));

            if (val != null) total += val;
        }

        document.add(table);

        Paragraph totals = new Paragraph(String.format("\nTotal Expenses: ₹ %.2f", total))
                .setTextAlignment(TextAlignment.RIGHT)
                .setBold();
        document.add(totals);

        document.close();
    }

    // ----------------- helpers -----------------

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /**
     * Try to parse amount-like objects to Double. Return null if not parseable.
     */
    private static Double parseNumberSafely(Object o) {
        if (o == null) return null;
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            s = s.replaceAll(",", ""); // allow "1,234.56"
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Try to read the date from Expense.getDate() handling common types.
     * Adjust this if your Expense model uses a different field name/type.
     */
    private static Object safeGetDate(Expense e) {
        if (e == null) return null;
        try {
            Object d = e.getDate(); // expecting LocalDate / LocalDateTime / java.util.Date / String
            if (d instanceof LocalDate || d instanceof LocalDateTime) return d;
            if (d == null) return null;
            // If it's java.util.Date, convert to LocalDate
            if (d instanceof java.util.Date) {
                java.util.Date ud = (java.util.Date) d;
                return ud.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
            // fallback to string
            return d.toString();
        } catch (NoSuchMethodError | NoClassDefFoundError | Exception ex) {
            // if your Expense class doesn't have getDate() or it's some strange type, fall back
            return null;
        }
    }

    private static String formatDateForPdf(Expense e) {
        Object d = safeGetDate(e);
        if (d instanceof LocalDate) return ((LocalDate) d).format(DATE_FMT);
        if (d instanceof LocalDateTime) return ((LocalDateTime) d).format(DATE_TIME_FMT);
        return d == null ? "" : d.toString();
    }
}



