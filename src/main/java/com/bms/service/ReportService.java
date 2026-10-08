package com.bms.service;

import com.bms.dao.ReportDao;
import com.bms.dao.SettingDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.ReportResult;
import com.bms.model.ReportResult.ColumnType;
import com.bms.util.MoneyFormatter;
import com.bms.util.Session;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the reports. Reports are an ADMINISTRATOR-only area: every method refuses an employee.
 * Sales reports count COMPLETED orders only, inside a date range (both days included).
 */
public class ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /** The reports the screen offers. needsDates = whether the date range is used. */
    public enum ReportType {
        SALES_BY_PRODUCT("Sales by product", true),
        SALES_BY_EMPLOYEE("Sales by employee", true),
        SALES_BY_CUSTOMER("Sales by customer", true),
        SALES_BY_DAY("Sales by day", true),
        STOCK_LEVELS("Stock levels and value", false),
        LOW_STOCK("Low stock (to reorder)", false);

        private final String label;
        private final boolean needsDates;

        ReportType(String label, boolean needsDates) {
            this.label = label;
            this.needsDates = needsDates;
        }

        public String getLabel() {
            return label;
        }

        public boolean needsDates() {
            return needsDates;
        }
    }

    private final ReportDao reportDao = new ReportDao();
    private final SettingDao settingDao = new SettingDao();

    /** Turns typed text such as 2026-10-01 into a date, or explains what is wrong. fieldName is used in the message. */
    public static LocalDate parseDate(String text, String fieldName) throws ValidationException {
        String t = (text == null) ? "" : text.trim();
        if (t.isEmpty()) {
            throw new ValidationException("The " + fieldName + " date is required (example: 2026-10-01).");
        }
        try {
            return LocalDate.parse(t, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new ValidationException("The " + fieldName + " date must be a real date written as yyyy-MM-dd (example: 2026-10-01).");
        }
    }

    public String getCurrencySymbol() throws BmsException {
        Session.requireAdmin();
        try {
            return settingDao.getValue("currency_symbol", "R");
        } catch (SQLException e) {
            throw databaseProblem("loading settings", "The settings could not be loaded. Please try again.", e);
        }
    }

    /** Runs one report. For stock reports the dates are ignored (they may be null). */
    public ReportResult run(ReportType type, LocalDate from, LocalDate to) throws BmsException {
        Session.requireAdmin();
        if (type == null) {
            throw new ValidationException("Please choose a report.");
        }
        if (type.needsDates()) {
            if (from == null || to == null) {
                throw new ValidationException("Please enter both dates.");
            }
            if (from.isAfter(to)) {
                throw new ValidationException("The 'from' date must not be after the 'to' date.");
            }
        }
        String symbol = getCurrencySymbol();
        try {
            return switch (type) {
                case SALES_BY_PRODUCT -> salesReport(type, from, to, symbol,
                        new String[]{"Product", "Units sold", "Revenue"},
                        new ColumnType[]{ColumnType.TEXT, ColumnType.NUMBER, ColumnType.MONEY},
                        reportDao.salesByProduct(from.toString(), to.toString()));
                case SALES_BY_EMPLOYEE -> salesReport(type, from, to, symbol,
                        new String[]{"Employee", "Orders", "Revenue"},
                        new ColumnType[]{ColumnType.TEXT, ColumnType.NUMBER, ColumnType.MONEY},
                        reportDao.salesByEmployee(from.toString(), to.toString()));
                case SALES_BY_CUSTOMER -> salesReport(type, from, to, symbol,
                        new String[]{"Customer", "Orders", "Revenue"},
                        new ColumnType[]{ColumnType.TEXT, ColumnType.NUMBER, ColumnType.MONEY},
                        reportDao.salesByCustomer(from.toString(), to.toString()));
                case SALES_BY_DAY -> salesReport(type, from, to, symbol,
                        new String[]{"Date", "Orders", "Revenue"},
                        new ColumnType[]{ColumnType.TEXT, ColumnType.NUMBER, ColumnType.MONEY},
                        reportDao.salesByDay(from.toString(), to.toString()));
                case STOCK_LEVELS -> stockLevelsReport(symbol);
                case LOW_STOCK -> lowStockReport();
            };
        } catch (SQLException e) {
            throw databaseProblem("running the " + type.getLabel() + " report",
                    "The report could not be created. Please try again.", e);
        }
    }

    // ---------- building each report ----------

    /** Adds the summary line (orders, revenue, average, cancelled) that every sales report shows. */
    private ReportResult salesReport(ReportType type, LocalDate from, LocalDate to, String symbol,
                                     String[] headers, ColumnType[] types, List<Object[]> rows) throws SQLException {
        ReportResult result = new ReportResult(type.getLabel() + ", " + from + " to " + to, headers, types);
        result.getRows().addAll(rows);

        long[] totals = reportDao.salesTotals(from.toString(), to.toString());
        long orders = totals[0];
        long revenue = totals[1];
        long cancelled = totals[2];
        long average = (orders == 0) ? 0 : Math.round((double) revenue / orders);
        result.setSummary(orders + " completed order(s)   |   Revenue " + MoneyFormatter.format(revenue, symbol)
                + "   |   Average order " + MoneyFormatter.format(average, symbol)
                + "   |   " + cancelled + " cancelled (not counted)");
        return result;
    }

    private ReportResult stockLevelsReport(String symbol) throws SQLException {
        ReportResult result = new ReportResult("Stock levels and value",
                new String[]{"Product", "Category", "In stock", "Reorder at", "Status", "Unit price", "Stock value"},
                new ColumnType[]{ColumnType.TEXT, ColumnType.TEXT, ColumnType.NUMBER, ColumnType.NUMBER,
                    ColumnType.TEXT, ColumnType.MONEY, ColumnType.MONEY});
        long units = 0;
        long value = 0;
        for (Object[] row : reportDao.stockLevels()) {
            result.getRows().add(row);
            units += (Long) row[2];
            value += (Long) row[6];
        }
        result.setSummary(result.getRows().size() + " product(s)   |   " + units + " unit(s) in stock   |   Stock value "
                + MoneyFormatter.format(value, symbol) + " (at selling price)");
        return result;
    }

    private ReportResult lowStockReport() throws SQLException {
        ReportResult result = new ReportResult("Low stock (to reorder)",
                new String[]{"Product", "Category", "In stock", "Reorder at", "Status", "Supplier"},
                new ColumnType[]{ColumnType.TEXT, ColumnType.TEXT, ColumnType.NUMBER, ColumnType.NUMBER,
                    ColumnType.TEXT, ColumnType.TEXT});
        result.getRows().addAll(reportDao.lowStock());
        long outOfStock = result.getRows().stream().filter(r -> (Long) r[2] == 0).count();
        result.setSummary(result.getRows().size() + " product(s) need reordering   |   " + outOfStock + " out of stock");
        return result;
    }

    private DatabaseException databaseProblem(String action, String friendlyMessage, SQLException e) {
        LOGGER.log(Level.SEVERE, "Database error while " + action, e);
        return new DatabaseException(DatabaseErrors.friendly(friendlyMessage, e), e);
    }
}
