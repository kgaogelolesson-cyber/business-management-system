package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Supplier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the suppliers table lives here (and nowhere else). */
public class SupplierDao {

    /**
     * Active suppliers whose name, contact person, email or phone contains the search text.
     * An empty search text returns every active supplier. Each row also carries the number of
     * ACTIVE products that supplier provides.
     */
    public List<Supplier> search(String text) throws SQLException {
        String sql = "SELECT s.supplier_id, s.supplier_name, s.contact_person, s.email, s.phone, s.address, "
                + "       (SELECT COUNT(*) FROM products p WHERE p.supplier_id = s.supplier_id AND p.is_active = 1) AS product_count "
                + "FROM suppliers s "
                + "WHERE s.is_active = 1 AND ("
                + "   s.supplier_name LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(s.contact_person, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(s.email, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(s.phone, '') LIKE ? ESCAPE '\\') "
                + "ORDER BY s.supplier_name COLLATE NOCASE";

        String pattern = "%" + escapeLike(text) + "%";
        List<Supplier> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) {
                statement.setString(i, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Supplier s = new Supplier();
                    s.setSupplierId(rs.getLong("supplier_id"));
                    s.setSupplierName(rs.getString("supplier_name"));
                    s.setContactPerson(rs.getString("contact_person"));
                    s.setEmail(rs.getString("email"));
                    s.setPhone(rs.getString("phone"));
                    s.setAddress(rs.getString("address"));
                    s.setProductCount(rs.getInt("product_count"));
                    result.add(s);
                }
            }
        }
        return result;
    }

    /** Saves a new supplier and returns the id the database gave it. */
    public long insert(Supplier supplier) throws SQLException {
        String sql = "INSERT INTO suppliers (supplier_name, contact_person, email, phone, address) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, supplier.getSupplierName());
            statement.setString(2, supplier.getContactPerson());
            statement.setString(3, supplier.getEmail());
            statement.setString(4, supplier.getPhone());
            statement.setString(5, supplier.getAddress());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    /** Updates an active supplier. Returns true if a row was changed. */
    public boolean update(Supplier supplier) throws SQLException {
        String sql = "UPDATE suppliers SET supplier_name = ?, contact_person = ?, email = ?, phone = ?, address = ? "
                + "WHERE supplier_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, supplier.getSupplierName());
            statement.setString(2, supplier.getContactPerson());
            statement.setString(3, supplier.getEmail());
            statement.setString(4, supplier.getPhone());
            statement.setString(5, supplier.getAddress());
            statement.setLong(6, supplier.getSupplierId());
            return statement.executeUpdate() == 1;
        }
    }

    /** "Deletes" a supplier by hiding it (is_active = 0). The row stays in the database. */
    public boolean deactivate(long supplierId) throws SQLException {
        String sql = "UPDATE suppliers SET is_active = 0 WHERE supplier_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            return statement.executeUpdate() == 1;
        }
    }

    /** How many ACTIVE products are supplied by this supplier. */
    public int countActiveProducts(long supplierId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE supplier_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Is another ACTIVE supplier already using this name? (capital letters ignored). excludeId = the supplier being edited, or 0. */
    public boolean nameInUse(String name, long excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suppliers WHERE is_active = 1 "
                + "AND LOWER(supplier_name) = LOWER(?) AND supplier_id <> ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setLong(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
