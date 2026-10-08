package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Product;
import com.bms.model.SupplierOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the products table lives here (and nowhere else). */
public class ProductDao {

    /**
     * Active products matching the search text (name, category, description or supplier).
     * category = null means "all categories". The stock status comes from the v_inventory view,
     * so the LOW STOCK / OUT OF STOCK rule is defined in exactly one place.
     */
    public List<Product> search(String text, String category) throws SQLException {
        return search(text, category, null);
    }

    /** As above, plus stockStatus = "IN STOCK" / "LOW STOCK" / "OUT OF STOCK" (null = every status). */
    public List<Product> search(String text, String category, String stockStatus) throws SQLException {
        String sql = "SELECT p.product_id, p.product_name, p.category, p.description, p.price_cents, "
                + "       p.quantity_in_stock, p.reorder_level, p.supplier_id, v.supplier_name, v.stock_status "
                + "FROM products p JOIN v_inventory v ON v.product_id = p.product_id "
                + "WHERE (? IS NULL OR LOWER(IFNULL(p.category, '')) = LOWER(?)) "
                + "  AND (? IS NULL OR v.stock_status = ?) "
                + "  AND (p.product_name LIKE ? ESCAPE '\\' "
                + "       OR IFNULL(p.category, '') LIKE ? ESCAPE '\\' "
                + "       OR IFNULL(p.description, '') LIKE ? ESCAPE '\\' "
                + "       OR IFNULL(v.supplier_name, '') LIKE ? ESCAPE '\\') "
                + "ORDER BY p.product_name COLLATE NOCASE";

        String pattern = "%" + escapeLike(text) + "%";
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setString(2, category);
            statement.setString(3, stockStatus);
            statement.setString(4, stockStatus);
            for (int i = 5; i <= 8; i++) {
                statement.setString(i, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readProduct(rs));
                }
            }
        }
        return result;
    }

    /** Each category used by an active product, once (capital letters ignored), in alphabetical order. */
    public List<String> findCategories() throws SQLException {
        String sql = "SELECT MIN(category) FROM products "
                + "WHERE is_active = 1 AND category IS NOT NULL AND TRIM(category) <> '' "
                + "GROUP BY LOWER(category) ORDER BY LOWER(MIN(category))";
        List<String> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    /** Active suppliers for the drop-down list in the product form. */
    public List<SupplierOption> findSupplierOptions() throws SQLException {
        String sql = "SELECT supplier_id, supplier_name FROM suppliers WHERE is_active = 1 "
                + "ORDER BY supplier_name COLLATE NOCASE";
        List<SupplierOption> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new SupplierOption(rs.getLong(1), rs.getString(2)));
            }
        }
        return result;
    }

    public boolean supplierExists(long supplierId) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM suppliers WHERE supplier_id = ?", supplierId) > 0;
    }

    /** Is another ACTIVE product already using this name? (capital letters ignored). excludeId = the product being edited, or 0. */
    public boolean nameInUse(String name, long excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE is_active = 1 "
                + "AND LOWER(product_name) = LOWER(?) AND product_id <> ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Saves a new product. If it starts with stock, an "Initial stock" line is also written to
     * stock_adjustments. Both inserts happen in ONE transaction: either both are saved or neither.
     */
    public long insert(Product product, long userId) throws SQLException {
        String productSql = "INSERT INTO products "
                + "(product_name, category, description, price_cents, quantity_in_stock, reorder_level, supplier_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String adjustmentSql = "INSERT INTO stock_adjustments (product_id, user_id, quantity_change, reason) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long newId;
                try (PreparedStatement statement = connection.prepareStatement(productSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, product.getProductName());
                    statement.setString(2, product.getCategory());
                    statement.setString(3, product.getDescription());
                    statement.setLong(4, product.getPriceCents());
                    statement.setInt(5, product.getQuantityInStock());
                    statement.setInt(6, product.getReorderLevel());
                    setNullableLong(statement, 7, product.getSupplierId());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        newId = keys.next() ? keys.getLong(1) : 0;
                    }
                }
                if (product.getQuantityInStock() > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(adjustmentSql)) {
                        statement.setLong(1, newId);
                        statement.setLong(2, userId);
                        statement.setInt(3, product.getQuantityInStock());
                        statement.setString(4, "Initial stock when the product was added");
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return newId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Updates the details of an active product. The stock quantity is NOT changed here:
     * stock only changes through orders and (Phase 12) stock adjustments.
     */
    public boolean update(Product product) throws SQLException {
        String sql = "UPDATE products SET product_name = ?, category = ?, description = ?, "
                + "price_cents = ?, reorder_level = ?, supplier_id = ? "
                + "WHERE product_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getProductName());
            statement.setString(2, product.getCategory());
            statement.setString(3, product.getDescription());
            statement.setLong(4, product.getPriceCents());
            statement.setInt(5, product.getReorderLevel());
            setNullableLong(statement, 6, product.getSupplierId());
            statement.setLong(7, product.getProductId());
            return statement.executeUpdate() == 1;
        }
    }

    /** "Deletes" a product by hiding it (is_active = 0). The row stays so old orders still make sense. */
    public boolean deactivate(long productId) throws SQLException {
        String sql = "UPDATE products SET is_active = 0 WHERE product_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            return statement.executeUpdate() == 1;
        }
    }

    /** How many order lines (on any order, any status) contain this product. */
    public int countOrderLines(long productId) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM order_items WHERE product_id = ?", productId);
    }

    // ---------- helpers ----------

    private int queryCount(String sql, long id) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private Product readProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getLong("product_id"));
        p.setProductName(rs.getString("product_name"));
        p.setCategory(rs.getString("category"));
        p.setDescription(rs.getString("description"));
        p.setPriceCents(rs.getLong("price_cents"));
        p.setQuantityInStock(rs.getInt("quantity_in_stock"));
        p.setReorderLevel(rs.getInt("reorder_level"));
        long supplierId = rs.getLong("supplier_id");
        p.setSupplierId(rs.wasNull() ? null : supplierId);
        p.setSupplierName(rs.getString("supplier_name"));
        p.setStockStatus(rs.getString("stock_status"));
        return p;
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
