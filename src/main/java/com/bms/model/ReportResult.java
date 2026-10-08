package com.bms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The finished answer of one report: a title, a one-line summary, column headings and rows.
 * Each column has a type so the screen and the CSV export know how to show it:
 * TEXT = words, NUMBER = whole number (such as units), MONEY = cents (68498 is shown as R684.98).
 * Rows hold a String for TEXT columns and a Long for NUMBER and MONEY columns.
 */
public class ReportResult {

    public enum ColumnType { TEXT, NUMBER, MONEY }

    private final String title;
    private final String[] headers;
    private final ColumnType[] types;
    private final List<Object[]> rows = new ArrayList<>();
    private String summary = "";

    public ReportResult(String title, String[] headers, ColumnType[] types) {
        this.title = title;
        this.headers = headers;
        this.types = types;
    }

    public String getTitle() { return title; }
    public String[] getHeaders() { return headers; }
    public ColumnType[] getTypes() { return types; }
    public List<Object[]> getRows() { return rows; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
