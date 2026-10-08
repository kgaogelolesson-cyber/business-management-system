package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.DashboardSummary.LowStockItem;
import com.bms.model.DashboardSummary.RecentCustomer;
import com.bms.model.DashboardSummary.RecentOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** The SQL queries behind the dashboard. Nothing here is hard-coded: it all comes from the tables. */
public class DashboardDao {

    public int countActiveCustomers() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM customers WHERE is_active = 1");
    }

    public int countActiveProducts() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM products WHERE is_active = 1");
    }

    public int countActiveEmployees() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM employees WHERE is_active = 1");
    }

    /** Cancelled orders are not counted. */
    public int countCompletedOrders() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'");
    }

    /** Cancelled orders are not counted. Returns cents. */
    public long sumRevenueCents() throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount_cents), 0) FROM orders WHERE status = 'COMPLETED'";
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Products that are LOW STOCK or OUT OF STOCK (the view decides the status). */
    public int countLowStockProducts() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM v_inventory WHERE stock_status <> 'IN STOCK'");
    }

    public List<LowStockItem> findLowStockItems(int limit) throws SQLException {
        String sql = "SELECT product_name, quantity_in_stock, reorder_level, stock_status "
                + "FROM v_inventory WHERE stock_status <> 'IN STOCK' "
                + "ORDER BY quantity_in_stock ASC, product_name ASC LIMIT ?";
        List<LowStockItem> items = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new LowStockItem(
                            rs.getString("product_name"),
                            rs.getInt("quantity_in_stock"),
                            rs.getInt("reorder_level"),
                            rs.getString("stock_status")));
                }
            }
        }
        return items;
    }

    /**
     * Newest orders first. If employeeId is not null, only that employee's orders are returned.
     */
    public List<RecentOrder> findRecentOrders(int limit, Long employeeId) throws SQLException {
        String sql = "SELECT order_id, order_date, customer_name, employee_name, total_amount_cents, status "
                + "FROM v_order_summary "
                + (employeeId != null ? "WHERE employee_id = ? " : "")
                + "ORDER BY order_date DESC, order_id DESC LIMIT ?";
        List<RecentOrder> orders = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            if (employeeId != null) {
                statement.setLong(parameter++, employeeId);
            }
            statement.setInt(parameter, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    orders.add(new RecentOrder(
                            rs.getLong("order_id"),
                            rs.getString("order_date"),
                            rs.getString("customer_name"),
                            rs.getString("employee_name"),
                            rs.getLong("total_amount_cents"),
                            rs.getString("status")));
                }
            }
        }
        return orders;
    }

    public List<RecentCustomer> findRecentCustomers(int limit) throws SQLException {
        String sql = "SELECT first_name || ' ' || last_name AS customer_name, email, created_at "
                + "FROM customers WHERE is_active = 1 "
                + "ORDER BY created_at DESC, customer_id DESC LIMIT ?";
        List<RecentCustomer> customers = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    customers.add(new RecentCustomer(
                            rs.getString("customer_name"),
                            rs.getString("email"),
                            rs.getString("created_at")));
                }
            }
        }
        return customers;
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
