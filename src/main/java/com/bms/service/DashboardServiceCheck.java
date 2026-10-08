package com.bms.service;

import com.bms.dao.UserDao;
import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.model.DashboardSummary;
import com.bms.model.DashboardSummary.RecentOrder;
import com.bms.util.MoneyFormatter;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A console self-test for the dashboard: it compares the dashboard figures with
 * plain, separate SQL counts. It changes nothing in the database.
 * Run it on its own: right-click -> Run File.
 */
public final class DashboardServiceCheck {

    private static int passed = 0;
    private static int total = 0;

    private DashboardServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            DashboardService dashboard = new DashboardService();
            AuthService auth = new AuthService();

            // --- Money formatting ---
            check("68498 cents is shown as R684.98", "R684.98".equals(MoneyFormatter.format(68498, "R")));
            check("123456789 cents is shown as R1,234,567.89", "R1,234,567.89".equals(MoneyFormatter.format(123456789, "R")));
            check("0 cents is shown as R0.00", "R0.00".equals(MoneyFormatter.format(0, "R")));

            // --- Nobody logged in ---
            Session.end();
            boolean refused = false;
            try {
                dashboard.getSummary();
            } catch (AccessDeniedException e) {
                refused = true;
            }
            check("Dashboard refuses a user who is not logged in", refused);

            // --- Administrator ---
            auth.login("admin", "Admin@123");
            DashboardSummary admin = dashboard.getSummary();
            System.out.println();
            System.out.println("Administrator dashboard: customers=" + admin.totalCustomers()
                    + ", products=" + admin.totalProducts() + ", employees=" + admin.totalEmployees()
                    + ", orders=" + admin.totalOrders()
                    + ", revenue=" + MoneyFormatter.format(admin.totalRevenueCents(), admin.currencySymbol())
                    + ", low stock=" + admin.lowStockCount());
            System.out.println();

            check("Administrator gets the full view", admin.fullView());
            check("Customer total matches the database",
                    admin.totalCustomers() == number("SELECT COUNT(*) FROM customers WHERE is_active = 1"));
            check("Product total matches the database",
                    admin.totalProducts() == number("SELECT COUNT(*) FROM products WHERE is_active = 1"));
            check("Employee total matches the database",
                    admin.totalEmployees() == number("SELECT COUNT(*) FROM employees WHERE is_active = 1"));
            check("Order total matches the database",
                    admin.totalOrders() == number("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'"));
            check("Revenue matches the sum of the order items",
                    admin.totalRevenueCents() == number("SELECT COALESCE(SUM(i.subtotal_cents), 0) FROM order_items i "
                            + "JOIN orders o ON o.order_id = i.order_id WHERE o.status = 'COMPLETED'"));
            check("Low-stock total matches the products table",
                    admin.lowStockCount() == number("SELECT COUNT(*) FROM products "
                            + "WHERE is_active = 1 AND quantity_in_stock <= reorder_level"));
            check("Low-stock list is no longer than the count",
                    admin.lowStockItems().size() == Math.min(8, admin.lowStockCount()));
            check("Recent orders are newest first", newestFirst(admin.recentOrders()));
            auth.logout();

            // --- Employee (only if the demo user exists) ---
            if (new UserDao().findByUsername("thandi") != null) {
                auth.login("thandi", "Employee@123");
                DashboardSummary employee = dashboard.getSummary();
                String myName = Session.getCurrentEmployee().getFullName();

                check("Employee gets the limited view", !employee.fullView());
                check("Employee does not receive employee, order or revenue totals",
                        employee.totalEmployees() == 0 && employee.totalOrders() == 0
                                && employee.totalRevenueCents() == 0);
                check("Employee still sees customers and products totals",
                        employee.totalCustomers() == admin.totalCustomers()
                                && employee.totalProducts() == admin.totalProducts());
                check("Employee sees no recent-customers list", employee.recentCustomers().isEmpty());
                check("Employee sees only their OWN orders",
                        employee.recentOrders().stream().allMatch(order -> myName.equals(order.employeeName())));
                auth.logout();
            } else {
                System.out.println("SKIPPED: employee checks (run DemoDataLoader first to create the user 'thandi').");
            }

            System.out.println();
            System.out.println(passed + " of " + total + " checks passed.");
        } catch (SQLException | BmsException e) {
            System.err.println("Could not run the checks: " + e.getMessage());
        }
    }

    private static boolean newestFirst(List<RecentOrder> orders) {
        for (int i = 0; i + 1 < orders.size(); i++) {
            if (orders.get(i).orderDate().compareTo(orders.get(i + 1).orderDate()) < 0) {
                return false;
            }
        }
        return true;
    }

    /** A separate, plain SQL query used to double-check the dashboard's numbers. */
    private static long number(String sql) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void check(String description, boolean ok) {
        total++;
        if (ok) {
            passed++;
        }
        System.out.println((ok ? "PASS: " : "FAIL: ") + description);
    }
}
