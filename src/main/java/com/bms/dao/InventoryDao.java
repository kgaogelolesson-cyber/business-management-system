package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Product;
import com.bms.model.StockAdjustment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the inventory screen lives here. The stock level itself is the
 * quantity_in_stock column of the products table; every change to it is also
 * written to stock_adjustments, so the history always explains the number.
 */
public class InventoryDao {

    /** How many history lines are loaded for one product. */
    private static final int HISTORY_LIMIT = 200;

    /**
     * Active products whose name, category or supplier contains the text.
     * statusFilter is a stock status from the v_inventory view ("IN STOCK", "LOW STOCK",
     * "OUT OF STOCK"), or null for every status.
     */
    public List<Product> search(String text, String statusFilter) throws SQLException {
        String sql = "SELECT product_id, product_name, category, quantity_in_stock, reorder_level, "
                + "       stock_status, supplier_name "
                + "FROM v_inventory "
                + "WHERE (? IS NULL OR stock_status = ?) "
                + "  AND (product_name LIKE ? ESCAPE '\\' "
                + "       OR IFNULL(category, '') LIKE ? ESCAPE '\\' "
                + "       OR IFNULL(supplier_name, '') LIKE ? ESCAPE '\\') "
                + "ORDER BY product_name COLLATE NOCASE";

        String pattern = "%" + escapeLike(text) + "%";
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, statusFilter);
            statement.setString(2, statusFilter);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, pattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product();
                    p.setProductId(rs.getLong("product_id"));
                    p.setProductName(rs.getString("product_name"));
                    p.setCategory(rs.getString("category"));
                    p.setQuantityInStock(rs.getInt("quantity_in_stock"));
                    p.setReorderLevel(rs.getInt("reorder_level"));
                    p.setStockStatus(rs.getString("stock_status"));
                    p.setSupplierName(rs.getString("supplier_name"));
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** The newest history lines of one product, newest first. */
    public List<StockAdjustment> findHistory(long productId) throws SQLException {
        String sql = "SELECT a.adjusted_at, a.quantity_change, a.reason, "
                + "       IFNULL(e.first_name || ' ' || e.last_name, u.username) AS adjusted_by "
                + "FROM stock_adjustments a "
                + "JOIN users u ON u.user_id = a.user_id "
                + "LEFT JOIN employees e ON e.employee_id = u.employee_id "
                + "WHERE a.product_id = ? "
                + "ORDER BY a.adjusted_at DESC, a.adjustment_id DESC "
                + "LIMIT " + HISTORY_LIMIT;

        List<StockAdjustment> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new StockAdjustment(
                            rs.getString("adjusted_at"),
                            rs.getInt("quantity_change"),
                            rs.getString("reason"),
                            rs.getString("adjusted_by")));
                }
            }
        }
        return result;
    }

    /** The current stock of an ACTIVE product, or -1 if there is no such active product. */
    public int findStock(long productId) throws SQLException {
        String sql = "SELECT quantity_in_stock FROM products WHERE product_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Changes the stock AND writes the history line, as ONE transaction.
     * The UPDATE only succeeds if the stock would not drop below 0, so two people
     * adjusting at the same moment can never push the stock below zero.
     * Returns the new stock, or -1 if nothing was changed (product gone, or not enough stock).
     */
    public int adjust(long productId, int change, String reason, long userId) throws SQLException {
        String updateSql = "UPDATE products SET quantity_in_stock = quantity_in_stock + ? "
                + "WHERE product_id = ? AND is_active = 1 AND quantity_in_stock + ? >= 0";
        String historySql = "INSERT INTO stock_adjustments (product_id, user_id, quantity_change, reason) "
                + "VALUES (?, ?, ?, ?)";
        String readSql = "SELECT quantity_in_stock FROM products WHERE product_id = ?";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setInt(1, change);
                    statement.setLong(2, productId);
                    statement.setInt(3, change);
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return -1;
                }
                try (PreparedStatement statement = connection.prepareStatement(historySql)) {
                    statement.setLong(1, productId);
                    statement.setLong(2, userId);
                    statement.setInt(3, change);
                    statement.setString(4, reason);
                    statement.executeUpdate();
                }
                int newStock;
                try (PreparedStatement statement = connection.prepareStatement(readSql)) {
                    statement.setLong(1, productId);
                    try (ResultSet rs = statement.executeQuery()) {
                        newStock = rs.next() ? rs.getInt(1) : -1;
                    }
                }
                connection.commit();
                return newStock;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
