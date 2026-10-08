package com.bms.util;

import com.bms.model.ReportResult;
import com.bms.model.ReportResult.ColumnType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves a report as a CSV file (comma-separated values) that Excel, LibreOffice or Google Sheets can open.
 * Money is written as plain numbers with two decimals (684.98), without the currency symbol,
 * so the spreadsheet can calculate with it.
 */
public final class ReportExporter {

    private ReportExporter() {
    }

    /** The whole report as CSV text: a title line, the column headings, then one line per row. */
    public static String toCsv(ReportResult report) {
        StringBuilder csv = new StringBuilder();
        csv.append(cell(report.getTitle(), ColumnType.TEXT)).append("\r\n");

        String[] headers = report.getHeaders();
        for (int c = 0; c < headers.length; c++) {
            csv.append(c > 0 ? "," : "").append(cell(headers[c], ColumnType.TEXT));
        }
        csv.append("\r\n");

        for (Object[] row : report.getRows()) {
            for (int c = 0; c < row.length; c++) {
                csv.append(c > 0 ? "," : "").append(cell(row[c], report.getTypes()[c]));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    /** Writes the CSV to a file. The file starts with a UTF-8 marker so Excel shows accents correctly. */
    public static void write(ReportResult report, Path file) throws IOException {
        Files.writeString(file, "﻿" + toCsv(report), StandardCharsets.UTF_8);
    }

    private static String cell(Object value, ColumnType type) {
        if (value == null) {
            return "";
        }
        if (type == ColumnType.MONEY) {
            return BigDecimal.valueOf((Long) value, 2).toPlainString();   // 68498 -> 684.98
        }
        if (type == ColumnType.NUMBER) {
            return String.valueOf(value);
        }
        String text = value.toString();
        // A text cell that starts with = + - or @ could be run as a formula by a spreadsheet.
        // A leading apostrophe makes it plain text instead.
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
