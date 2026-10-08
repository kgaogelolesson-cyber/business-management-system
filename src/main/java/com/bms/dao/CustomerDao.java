package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the customers table lives here (and nowhere else). */
public class CustomerDao {

    private static final String COLUMNS =
            "customer_id, first_name, last_name, email, phone, address, created_at";

    /**
     * Active customers whose name, email or phone contains the search text.
     * An empty search text returns every active customer.
     */
    public List<Customer> search(String text) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customers "
                + "WHERE is_active = 1 AND ("
                + "   first_name LIKE ? ESCAPE '\\' OR last_name LIKE ? ESCAPE '\\' "
                + "   OR (first_name || ' ' || last_name) LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(email, '') LIKE ? ESCAPE '\\' OR IFNULL(phone, '') LIKE ? ESCAPE '\\') "
                + "ORDER BY last_name COLLATE NOCASE, first_name COLLATE NOCASE";

        String pattern = "%" + escapeLike(text) + "%";
        List<Customer> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                statement.setString(i, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readCustomer(rs));
                }
            }
        }
        return result;
    }

    /** Finds an ACTIVE customer by id. Returns null if there is none. */
    public Customer findById(long customerId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customers WHERE customer_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readCustomer(rs) : null;
            }
        }
    }

    /** Saves a new customer and returns the id the database gave it. */
    public long insert(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (first_name, last_name, email, phone, address) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, customer.getFirstName());
            statement.setString(2, customer.getLastName());
            statement.setString(3, customer.getEmail());
            statement.setString(4, customer.getPhone());
            statement.setString(5, customer.getAddress());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    /** Updates an active customer. Returns true if a row was changed. */
    public boolean update(Customer customer) throws SQLException {
        String sql = "UPDATE customers SET first_name = ?, last_name = ?, email = ?, phone = ?, address = ? "
                + "WHERE customer_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customer.getFirstName());
            statement.setString(2, customer.getLastName());
            statement.setString(3, customer.getEmail());
            statement.setString(4, customer.getPhone());
            statement.setString(5, customer.getAddress());
            statement.setLong(6, customer.getCustomerId());
            return statement.executeUpdate() == 1;
        }
    }

    /** "Deletes" a customer by hiding it (is_active = 0). The row and its history stay in the database. */
    public boolean deactivate(long customerId) throws SQLException {
        String sql = "UPDATE customers SET is_active = 0 WHERE customer_id = ? AND is_active = 1";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            return statement.executeUpdate() == 1;
        }
    }

    /** How many orders (any status) belong to this customer. */
    public int countOrders(long customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE customer_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Other ACTIVE customers with the same email or the same phone.
     * excludeId is the customer being edited (use 0 for a new customer).
     * A blank email/phone never counts as a match.
     */
    public List<Customer> findDuplicates(String email, String phone, long excludeId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customers "
                + "WHERE is_active = 1 AND customer_id <> ? AND ("
                + "   (? IS NOT NULL AND LOWER(IFNULL(email, '')) = LOWER(?)) "
                + "   OR (? IS NOT NULL AND IFNULL(phone, '') = ?)) "
                + "ORDER BY last_name COLLATE NOCASE";
        List<Customer> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, excludeId);
            statement.setString(2, email);
            statement.setString(3, email);
            statement.setString(4, phone);
            statement.setString(5, phone);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readCustomer(rs));
                }
            }
        }
        return result;
    }

    private Customer readCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("customer_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("created_at"));
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
