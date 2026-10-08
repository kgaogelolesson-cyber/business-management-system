package com.bms.database;

import com.bms.util.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * OPTIONAL: fills the database with sample data for testing and screenshots.
 * Run it on its own (right-click -> Run File). It does nothing if products already exist.
 */
public final class DemoDataLoader {

    public static final String DEMO_EMPLOYEE_USERNAME = "thandi";
    public static final String DEMO_EMPLOYEE_PASSWORD = "Employee@123";

    private DemoDataLoader() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            if (load()) {
                System.out.println("Demo data loaded.");
            } else {
                System.out.println("Demo data NOT loaded: products already exist.");
            }
            System.out.println(DatabaseInitializer.getDiagnostics());
        } catch (SQLException e) {
            System.err.println("FAILED to load demo data: " + e.getMessage());
        }
    }

    /** Returns true if the data was added, false if it was skipped. */
    public static boolean load() throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (count(c, "products") > 0) {
                    c.rollback();
                    return false;
                }

                // Employee + login (the order below is processed by this employee)
                long thandiEmployee = insert(c,
                        "INSERT INTO employees (first_name, last_name, email, position, department, hire_date) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                        "Thandi", "Mokoena", "thandi@example.com", "Sales Clerk", "Sales", "2026-02-01");
                String salt = PasswordHasher.generateSalt();
                insert(c, "INSERT INTO users (username, password_hash, salt, role, employee_id) VALUES (?, ?, ?, 'EMPLOYEE', ?)",
                        DEMO_EMPLOYEE_USERNAME, PasswordHasher.hash(DEMO_EMPLOYEE_PASSWORD, salt), salt, thandiEmployee);

                // Customers
                long sipho = insert(c, "INSERT INTO customers (first_name, last_name, email, phone, address) VALUES (?, ?, ?, ?, ?)",
                        "Sipho", "Dlamini", "sipho@example.com", "0831112222", "12 Church St, Polokwane");
                insert(c, "INSERT INTO customers (first_name, last_name, email, phone, address) VALUES (?, ?, ?, ?, ?)",
                        "Karabo", "Molefe", "karabo@example.com", "0153334444", "5 Industrial Rd, Polokwane");

                // Suppliers
                long techSource = insert(c, "INSERT INTO suppliers (supplier_name, contact_person, email, phone, address) VALUES (?, ?, ?, ?, ?)",
                        "TechSource Distributors", "Mark Botha", "sales@techsource.example", "0114445555", "Johannesburg");
                long officeWorld = insert(c, "INSERT INTO suppliers (supplier_name, contact_person, email, phone, address) VALUES (?, ?, ?, ?, ?)",
                        "OfficeWorld Wholesale", "Lerato Nkosi", "orders@officeworld.example", "0126667777", "Pretoria");

                // Products (prices in cents: 12999 = R129.99)
                String productSql = "INSERT INTO products (product_name, category, price_cents, quantity_in_stock, reorder_level, supplier_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
                long usb = insert(c, productSql, "USB Flash Drive 32GB", "Accessories", 12999, 50, 10, techSource);
                long mouse = insert(c, productSql, "Wireless Mouse", "Accessories", 24900, 3, 5, techSource);       // LOW STOCK
                long paper = insert(c, productSql, "A4 Paper Ream", "Stationery", 8500, 100, 20, officeWorld);
                long pens = insert(c, productSql, "Ballpoint Pens (Box of 50)", "Stationery", 6000, 30, 10, officeWorld);
                insert(c, productSql, "Desk Stapler", "Stationery", 4500, 0, 2, officeWorld);          // OUT OF STOCK

                // Like the real screens, every stock level starts with an "Initial stock" history line,
                // so that each product's history adds up to its stock.
                long adminUser = queryLong(c, "SELECT MIN(user_id) FROM users WHERE role = 'ADMIN'");
                addHistory(c, usb, adminUser, 50, "Initial stock");
                addHistory(c, mouse, adminUser, 3, "Initial stock");
                addHistory(c, paper, adminUser, 100, "Initial stock");
                addHistory(c, pens, adminUser, 30, "Initial stock");

                // One order: 2 x USB (12999) + 5 x paper (8500) = 68498. Saved with its items and stock reduction.
                long orderId = insert(c, "INSERT INTO orders (customer_id, employee_id, total_amount_cents) VALUES (?, ?, ?)",
                        sipho, thandiEmployee, 2 * 12999 + 5 * 8500);
                addOrderLine(c, orderId, usb, 2, 12999);
                addOrderLine(c, orderId, paper, 5, 8500);
                addHistory(c, usb, adminUser, -2, "Sold on order #" + orderId);
                addHistory(c, paper, adminUser, -5, "Sold on order #" + orderId);

                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void addOrderLine(Connection c, long orderId, long productId, int quantity, int unitPriceCents)
            throws SQLException {
        insert(c, "INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents, subtotal_cents) "
                + "VALUES (?, ?, ?, ?, ?)", orderId, productId, quantity, unitPriceCents, quantity * unitPriceCents);
        // Same safe pattern the real order code will use in Phase 13
        int changed = execute(c, "UPDATE products SET quantity_in_stock = quantity_in_stock - ? "
                + "WHERE product_id = ? AND quantity_in_stock >= ?", quantity, productId, quantity);
        if (changed == 0) {
            throw new SQLException("Not enough stock for product " + productId);
        }
    }

    private static void addHistory(Connection c, long productId, long userId, int change, String reason) throws SQLException {
        insert(c, "INSERT INTO stock_adjustments (product_id, user_id, quantity_change, reason) VALUES (?, ?, ?, ?)",
                productId, userId, change, reason);
    }

    private static long queryLong(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static int count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Runs an INSERT and returns the new row's ID. */
    private static long insert(Connection c, String sql, Object... params) throws SQLException {
        execute(c, sql, params);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Runs an INSERT/UPDATE with prepared-statement parameters; returns rows changed. */
    private static int execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }
}
