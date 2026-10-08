package com.bms.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A small self-test: tries to insert BAD data and expects the database to refuse it.
 * Run it on its own (right-click -> Run File). Nothing is ever saved.
 */
public final class SchemaCheck {

    private static int passed = 0;
    private static int total = 0;

    private SchemaCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();

            check("Negative stock is rejected",
                    "INSERT INTO products (product_name, price_cents, quantity_in_stock) VALUES ('Bad', 100, -5)");
            check("Negative price is rejected",
                    "INSERT INTO products (product_name, price_cents, quantity_in_stock) VALUES ('Bad', -100, 5)");
            check("Order for a customer that does not exist is rejected (foreign key)",
                    "INSERT INTO orders (customer_id, employee_id, total_amount_cents) "
                    + "VALUES (999999, (SELECT MIN(employee_id) FROM employees), 0)");
            check("Duplicate username is rejected, even in capital letters",
                    "INSERT INTO users (username, password_hash, salt, role) VALUES ('ADMIN', 'x', 'y', 'ADMIN')");
            check("Unknown role is rejected",
                    "INSERT INTO users (username, password_hash, salt, role) VALUES ('hacker', 'x', 'y', 'SUPERUSER')");

            System.out.println();
            System.out.println(passed + " of " + total + " checks passed.");
        } catch (SQLException e) {
            System.err.println("Could not run the checks: " + e.getMessage());
        }
    }

    /** The statement is EXPECTED to fail. If it succeeds, the check fails. */
    private static void check(String description, String badSql) throws SQLException {
        total++;
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.executeUpdate(badSql);
                System.out.println("FAIL: " + description + "  (the database ACCEPTED bad data)");
            } catch (SQLException expected) {
                passed++;
                System.out.println("PASS: " + description);
                System.out.println("      database said: " + expected.getMessage());
            } finally {
                c.rollback(); // never keep anything from these tests
            }
        }
    }
}
