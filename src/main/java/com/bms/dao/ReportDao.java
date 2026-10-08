package com.bms.dao;

import com.bms.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for reports lives here. Every report is a read-only SELECT.
 * Sales reports count COMPLETED orders only (cancelled orders are left out) and take a date range:
 * fromDate and toDate are text like "2026-10-01", both days included.
 * Each row comes back as an Object[]: String for words, Long for numbers and money (in cents).
 */
public class ReportDao {

    public List<Object[]> salesByProduct(String fromDate, String toDate) throws SQLException {
        String sql = "SELECT p.product_name, SUM(oi.quantity) AS units, SUM(oi.subtotal_cents) AS revenue "
                + "FROM order_items oi "
                + "JOIN orders o ON o.order_id = oi.order_id "
                + "JOIN products p ON p.product_id = oi.product_id "
                + "WHERE o.status = 'COMPLETED' AND date(o.order_date) BETWEEN ? AND ? "
                + "GROUP BY p.product_id "
                + "ORDER BY revenue DESC, p.product_name COLLATE NOCASE";
        return run(sql, new String[]{fromDate, toDate}, 1, 2);
    }

    public List<Object[]> salesByEmployee(String fromDate, String toDate) throws SQLException {
        String sql = "SELECT e.first_name || ' ' || e.last_name AS employee, COUNT(*) AS orders, "
                + "       SUM(o.total_amount_cents) AS revenue "
                + "FROM orders o JOIN employees e ON e.employee_id = o.employee_id "
                + "WHERE o.status = 'COMPLETED' AND date(o.order_date) BETWEEN ? AND ? "
                + "GROUP BY e.employee_id "
                + "ORDER BY revenue DESC, employee COLLATE NOCASE";
        return run(sql, new String[]{fromDate, toDate}, 1, 2);
    }

    public List<Object[]> salesByCustomer(String fromDate, String toDate) throws SQLException {
        String sql = "SELECT c.first_name || ' ' || c.last_name AS customer, COUNT(*) AS orders, "
                + "       SUM(o.total_amount_cents) AS revenue "
                + "FROM orders o JOIN customers c ON c.customer_id = o.customer_id "
                + "WHERE o.status = 'COMPLETED' AND date(o.order_date) BETWEEN ? AND ? "
                + "GROUP BY c.customer_id "
                + "ORDER BY revenue DESC, customer COLLATE NOCASE";
        return run(sql, new String[]{fromDate, toDate}, 1, 2);
    }

    public List<Object[]> salesByDay(String fromDate, String toDate) throws SQLException {
        String sql = "SELECT date(o.order_date) AS day, COUNT(*) AS orders, SUM(o.total_amount_cents) AS revenue "
                + "FROM orders o "
                + "WHERE o.status = 'COMPLETED' AND date(o.order_date) BETWEEN ? AND ? "
                + "GROUP BY day ORDER BY day";
        return run(sql, new String[]{fromDate, toDate}, 1, 2);
    }

    /** Order count, revenue (cents) and cancelled-order count inside the date range. */
    public long[] salesTotals(String fromDate, String toDate) throws SQLException {
        String sql = "SELECT "
                + "  COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0), "
                + "  COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount_cents ELSE 0 END), 0), "
                + "  COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) "
                + "FROM orders WHERE date(order_date) BETWEEN ? AND ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fromDate);
            statement.setString(2, toDate);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
            }
        }
    }

    /** Every active product with its stock and the value of that stock (quantity x price). */
    public List<Object[]> stockLevels() throws SQLException {
        String sql = "SELECT p.product_name, IFNULL(p.category, ''), p.quantity_in_stock, p.reorder_level, "
                + "       v.stock_status, p.price_cents, p.quantity_in_stock * p.price_cents AS stock_value "
                + "FROM products p JOIN v_inventory v ON v.product_id = p.product_id "
                + "ORDER BY p.product_name COLLATE NOCASE";
        return run(sql, new String[0], 2, 3, 5, 6);
    }

    /** Active products at or below their reorder level (including 0), lowest stock first, with the supplier. */
    public List<Object[]> lowStock() throws SQLException {
        String sql = "SELECT product_name, IFNULL(category, ''), quantity_in_stock, reorder_level, "
                + "       stock_status, IFNULL(supplier_name, '') "
                + "FROM v_inventory "
                + "WHERE stock_status IN ('LOW STOCK', 'OUT OF STOCK') "
                + "ORDER BY quantity_in_stock ASC, product_name COLLATE NOCASE";
        return run(sql, new String[0], 2, 3);
    }

    // ---------- helper ----------

    /**
     * Runs a SELECT and returns its rows. numberColumns lists the 0-based columns that are numbers
     * (read as Long); every other column is read as text.
     */
    private List<Object[]> run(String sql, String[] parameters, int... numberColumns) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] row = new Object[columns];
                    for (int c = 0; c < columns; c++) {
                        row[c] = isNumber(numberColumns, c) ? (Object) rs.getLong(c + 1) : rs.getString(c + 1);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private boolean isNumber(int[] numberColumns, int column) {
        for (int n : numberColumns) {
            if (n == column) {
                return true;
            }
        }
        return false;
    }
}
