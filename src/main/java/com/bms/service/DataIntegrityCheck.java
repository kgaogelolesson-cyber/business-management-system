package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A console self-test that READS the whole database and checks that the business rules hold for ALL the data
 * (not just for the rows a test program adds): nothing is changed, so it is safe to run on your real data at any time.
 * Run it on its own: right-click -> Run File.
 *
 * Lines starting with INFO are notes, not failures: they describe data that is allowed but worth knowing about.
 */
public final class DataIntegrityCheck {

    private static int passed = 0;
    private static int total = 0;

    private DataIntegrityCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {

                // ---------- the database file itself ----------
                check("SQLite's own integrity check says 'ok'", "ok".equals(text(st, "PRAGMA integrity_check")));
                check("Every foreign key points at a row that exists", count(st, "SELECT COUNT(*) FROM pragma_foreign_key_check") == 0);
                check("Foreign keys are switched ON for our connections", count(st, "PRAGMA foreign_keys") == 1);

                // ---------- stock ----------
                check("No product has negative stock", count(st, "SELECT COUNT(*) FROM products WHERE quantity_in_stock < 0") == 0);
                check("No product has a negative price", count(st, "SELECT COUNT(*) FROM products WHERE price_cents < 0") == 0);
                check("The stock status in the inventory view matches the numbers",
                        count(st, "SELECT COUNT(*) FROM v_inventory WHERE "
                                + "(quantity_in_stock = 0 AND stock_status <> 'OUT OF STOCK') "
                                + "OR (quantity_in_stock > 0 AND quantity_in_stock <= reorder_level AND stock_status <> 'LOW STOCK') "
                                + "OR (quantity_in_stock > reorder_level AND stock_status <> 'IN STOCK')") == 0);
                check("Every stock history line has a reason and a real change",
                        count(st, "SELECT COUNT(*) FROM stock_adjustments WHERE quantity_change = 0 OR length(trim(reason)) = 0") == 0);

                // ---------- orders ----------
                check("Every order has at least one product line",
                        count(st, "SELECT COUNT(*) FROM orders o WHERE NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.order_id)") == 0);
                check("Every order's total equals the sum of its lines",
                        count(st, "SELECT COUNT(*) FROM orders o WHERE o.total_amount_cents <> "
                                + "(SELECT COALESCE(SUM(i.subtotal_cents), 0) FROM order_items i WHERE i.order_id = o.order_id)") == 0);
                check("Every line's subtotal equals quantity x price",
                        count(st, "SELECT COUNT(*) FROM order_items WHERE subtotal_cents <> quantity * unit_price_cents") == 0);
                check("Every line has a quantity above zero", count(st, "SELECT COUNT(*) FROM order_items WHERE quantity <= 0") == 0);
                check("Cancelled orders say when and by whom; completed orders do not",
                        count(st, "SELECT COUNT(*) FROM orders WHERE (status = 'CANCELLED' AND (cancelled_at IS NULL OR cancelled_by IS NULL)) "
                                + "OR (status = 'COMPLETED' AND (cancelled_at IS NOT NULL OR cancelled_by IS NOT NULL))") == 0);
                check("Every order belongs to a customer and an employee that exist",
                        count(st, "SELECT COUNT(*) FROM orders o WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.customer_id = o.customer_id) "
                                + "OR NOT EXISTS (SELECT 1 FROM employees e WHERE e.employee_id = o.employee_id)") == 0);

                // ---------- stock history that the program wrote itself ----------
                check("Every order that was sold through the screens has a matching 'Sold on order' line for each product",
                        count(st, "SELECT COUNT(*) FROM order_items i WHERE EXISTS "
                                + "(SELECT 1 FROM stock_adjustments a WHERE a.reason = 'Sold on order #' || i.order_id) "
                                + "AND NOT EXISTS (SELECT 1 FROM stock_adjustments a WHERE a.reason = 'Sold on order #' || i.order_id "
                                + "AND a.product_id = i.product_id AND a.quantity_change = -i.quantity)") == 0);
                check("Every cancelled order that has sale history also has the stock put back",
                        count(st, "SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id = i.order_id "
                                + "WHERE o.status = 'CANCELLED' AND EXISTS "
                                + "(SELECT 1 FROM stock_adjustments a WHERE a.reason = 'Sold on order #' || i.order_id) "
                                + "AND NOT EXISTS (SELECT 1 FROM stock_adjustments a WHERE a.reason = 'Order #' || i.order_id || ' cancelled' "
                                + "AND a.product_id = i.product_id AND a.quantity_change = i.quantity)") == 0);

                // ---------- people and passwords ----------
                check("At least one active administrator exists",
                        count(st, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = 1") >= 1);
                check("Every password is stored as a hash with a salt (never as plain text)",
                        count(st, "SELECT COUNT(*) FROM users WHERE length(password_hash) < 40 OR length(salt) < 10 "
                                + "OR password_hash IN ('Admin@123', 'Employee@123')") == 0);
                check("No two users share the same salt (each password is salted separately)",
                        count(st, "SELECT COUNT(*) FROM (SELECT salt FROM users GROUP BY salt HAVING COUNT(*) > 1)") == 0);
                check("Every active login belongs to an employee record that exists and is active, or has none",
                        count(st, "SELECT COUNT(*) FROM users u JOIN employees e ON e.employee_id = u.employee_id "
                                + "WHERE u.is_active = 1 AND e.is_active = 0") == 0);

                // ---------- settings ----------
                check("The currency symbol setting exists and is not blank",
                        count(st, "SELECT COUNT(*) FROM settings WHERE setting_key = 'currency_symbol' AND length(trim(setting_value)) > 0") == 1);
                check("The default low-stock level is a whole number above zero",
                        count(st, "SELECT COUNT(*) FROM settings WHERE setting_key = 'default_low_stock_level' "
                                + "AND CAST(setting_value AS INTEGER) > 0 AND CAST(setting_value AS INTEGER) || '' = setting_value") == 1);

                // ---------- notes (allowed, but good to know) ----------
                long noHistory = count(st, "SELECT COUNT(*) FROM products WHERE quantity_in_stock > 0 "
                        + "AND NOT EXISTS (SELECT 1 FROM stock_adjustments a WHERE a.product_id = products.product_id)");
                info(noHistory + " product(s) have stock but no stock history (normal for data loaded before stock history existed).");
                long mismatch = count(st, "SELECT COUNT(*) FROM products p WHERE EXISTS (SELECT 1 FROM stock_adjustments a WHERE a.product_id = p.product_id) "
                        + "AND p.quantity_in_stock <> (SELECT SUM(a.quantity_change) FROM stock_adjustments a WHERE a.product_id = p.product_id)");
                info(mismatch + " product(s) whose stock history does not add up to the stock (normal for older demo data; should be 0 for data made on a fresh database).");
                info(count(st, "SELECT COUNT(*) FROM customers") + " customers, " + count(st, "SELECT COUNT(*) FROM products") + " products, "
                        + count(st, "SELECT COUNT(*) FROM orders") + " orders, " + count(st, "SELECT COUNT(*) FROM users") + " users in the database.");
            }
        } catch (Exception e) {
            total++;
            System.out.println("FAIL: unexpected error: " + e);
            e.printStackTrace(System.out);
        }
        System.out.println();
        System.out.println(passed + " of " + total + " checks passed.");
        System.out.println(passed == total ? "ALL CHECKS PASSED." : "SOME CHECKS FAILED - read the FAIL lines above.");
    }

    // ---------- helpers ----------

    /** Runs a query that returns ONE number. The SQL is written above in this file, never typed by a user. */
    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private static String text(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static void info(String text) {
        System.out.println("INFO: " + text);
    }

    private static void check(String description, boolean ok) {
        total++;
        if (ok) {
            passed++;
            System.out.println("PASS: " + description);
        } else {
            System.out.println("FAIL: " + description);
        }
    }
}
