package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.model.CustomerOrder;
import com.bms.model.DashboardSummary;
import com.bms.model.Product;
import com.bms.model.ReportResult;
import com.bms.service.InventoryService.StockFilter;
import com.bms.service.OrderService.StatusFilter;
import com.bms.service.ReportService.ReportType;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A console self-test for volume: does the program stay correct and fast with thousands of rows?
 * It adds 3,000 TEST customers, 1,500 TEST products and 1,300 TEST orders (names starting "CheckVol"),
 * tests lists, searches, history, dashboard and reports, then removes ALL of its own rows again.
 * It needs the demo data (run DemoDataLoader once first). Takes a few seconds.
 * The speed checks use a very generous limit (3 seconds each) - they catch a broken query, not a slow computer.
 */
public final class VolumeCheck {

    private static final int CUSTOMERS = 3000;
    private static final int PRODUCTS = 1500;
    private static final int HEAVY_ORDERS = 1300;      // all for ONE customer, more than the list limit
    private static final long SLOW_MS = 3000;
    private static int passed = 0;
    private static int total = 0;

    private VolumeCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            auth.login("admin", "Admin@123");
            CustomerService customers = new CustomerService();
            ProductService products = new ProductService();
            OrderService orders = new OrderService();
            InventoryService inventory = new InventoryService();
            DashboardService dashboard = new DashboardService();
            ReportService reports = new ReportService();

            DashboardSummary before = dashboard.getSummary();
            long ordersBefore = count("SELECT COUNT(*) FROM orders");

            long started = System.currentTimeMillis();
            long heavyCustomerId = seed();
            info("Seeded " + CUSTOMERS + " customers, " + PRODUCTS + " products and " + HEAVY_ORDERS
                    + " orders in " + (System.currentTimeMillis() - started) + " ms");

            check("The list limit is 1000", OrderService.MAX_LIST_ROWS == 1000);

            // ---------- lists and searches ----------
            List<CustomerOrder> all = timed("Orders list (all)", () -> orders.search("", StatusFilter.ALL));
            check("The orders list is cut at exactly the limit (1000 of " + (ordersBefore + HEAVY_ORDERS) + ")",
                    all.size() == OrderService.MAX_LIST_ROWS);
            check("...and it holds the NEWEST orders first", isNewestFirst(all));
            List<CustomerOrder> heavy = timed("Orders search by customer name", () -> orders.search("CheckVolHeavy", StatusFilter.ALL));
            check("A search with more matches than the limit is also cut at the limit", heavy.size() == OrderService.MAX_LIST_ROWS);
            List<CustomerOrder> history = timed("Customer history (1300 orders)", () -> orders.getCustomerHistory(heavyCustomerId));
            check("A customer's history is cut at the limit too", history.size() == OrderService.MAX_LIST_ROWS);
            List<CustomerOrder> oneDay = timed("Orders for one day", () -> orders.search("CheckVolHeavy", StatusFilter.ALL, "2026-02-14", "2026-02-14", null));
            check("Narrowing by date gives a list below the limit (so nothing is hidden)",
                    !oneDay.isEmpty() && oneDay.size() < OrderService.MAX_LIST_ROWS);
            check("...and every order found really is from that day", oneDay.stream().allMatch(o -> o.getOrderDate().startsWith("2026-02-14")));
            long expectedFeb14 = count("SELECT COUNT(*) FROM orders WHERE customer_id = " + heavyCustomerId + " AND date(order_date) = '2026-02-14'");
            check("...and the number matches the database (" + expectedFeb14 + ")", oneDay.size() == expectedFeb14);

            List<?> customerList = timed("Customer list (all)", () -> customers.search(""));
            check("Every test customer appears in the customer list (no limit on customers)", customerList.size() >= CUSTOMERS);
            List<?> customerSearch = timed("Customer search", () -> customers.search("CheckVolCust"));
            check("A customer search finds all " + CUSTOMERS + " test customers", customerSearch.size() == CUSTOMERS);
            List<Product> productList = timed("Product list (all)", () -> products.search("CheckVol", null));
            check("Every test product appears in the product list", productList.size() == PRODUCTS);
            List<Product> low = timed("Product search with stock filter", () -> products.search("CheckVol", null, StockFilter.LOW_STOCK));
            check("The stock filter agrees with the stock numbers", low.stream().allMatch(p -> p.getQuantityInStock() <= p.getReorderLevel() && p.getQuantityInStock() > 0));
            List<Product> inventoryList = timed("Inventory list", () -> inventory.search("CheckVol", StockFilter.ALL));
            check("The inventory list shows every test product", inventoryList.size() == PRODUCTS);

            // ---------- totals are never limited ----------
            DashboardSummary after = timed("Dashboard", dashboard::getSummary);
            check("The dashboard counts ALL orders (not just the first 1000)", after.totalOrders() == before.totalOrders() + HEAVY_ORDERS);
            check("The dashboard counts all new customers", after.totalCustomers() == before.totalCustomers() + CUSTOMERS + 1);
            check("The dashboard counts all new products", after.totalProducts() == before.totalProducts() + PRODUCTS);

            long expectedHeavySales = count("SELECT COALESCE(SUM(total_amount_cents), 0) FROM orders WHERE customer_id = " + heavyCustomerId + " AND status = 'COMPLETED'");
            ReportResult byCustomer = timed("Report: sales by customer", () -> reports.run(ReportType.SALES_BY_CUSTOMER, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
            check("The sales-by-customer report includes the heavy customer", reportHasCustomer(byCustomer, "CheckVolHeavy"));
            Object[] heavyRow = byCustomer.getRows().stream().filter(r -> String.valueOf(r[0]).contains("CheckVolHeavy")).findFirst().orElse(null);
            check("The report counts ALL " + HEAVY_ORDERS + " orders of that customer (not just the newest 1000)",
                    heavyRow != null && ((Number) heavyRow[1]).longValue() == HEAVY_ORDERS);
            check("The report's revenue for that customer matches the database (" + expectedHeavySales + " cents)",
                    heavyRow != null && ((Number) heavyRow[2]).longValue() == expectedHeavySales);
            ReportResult byProduct = timed("Report: sales by product", () -> reports.run(ReportType.SALES_BY_PRODUCT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
            check("The sales-by-product report lists many products", byProduct.getRows().size() > 1000);
            ReportResult stock = timed("Report: stock levels", () -> reports.run(ReportType.STOCK_LEVELS, null, null));
            check("The stock report lists every product", stock.getRows().size() >= PRODUCTS);
            timed("Report: sales by day", () -> reports.run(ReportType.SALES_BY_DAY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
            timed("Report: low stock", () -> reports.run(ReportType.LOW_STOCK, null, null));

        } catch (Exception e) {
            total++;
            System.out.println("FAIL: unexpected error: " + e);
            e.printStackTrace(System.out);
        } finally {
            cleanUp();
            Session.end();
        }
        System.out.println();
        System.out.println(passed + " of " + total + " checks passed.");
        System.out.println(passed == total ? "ALL CHECKS PASSED." : "SOME CHECKS FAILED - read the FAIL lines above.");
    }

    // ---------- seeding and cleaning (direct SQL, one transaction) ----------

    private static long seed() throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                long employeeId = scalar(c, "SELECT MIN(employee_id) FROM employees");
                try (PreparedStatement cust = c.prepareStatement(
                        "INSERT INTO customers (first_name, last_name, email) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < CUSTOMERS; i++) {
                        cust.setString(1, "CheckVolCust" + i);
                        cust.setString(2, "Surname" + (i % 300));
                        cust.setString(3, "checkvol" + i + "@example.com");
                        cust.addBatch();
                    }
                    cust.executeBatch();
                }
                try (PreparedStatement cust = c.prepareStatement(
                        "INSERT INTO customers (first_name, last_name) VALUES ('CheckVolHeavy', 'Buyer')")) {
                    cust.executeUpdate();
                }
                long heavy = scalar(c, "SELECT customer_id FROM customers WHERE first_name = 'CheckVolHeavy'");
                try (PreparedStatement prod = c.prepareStatement(
                        "INSERT INTO products (product_name, category, price_cents, quantity_in_stock, reorder_level) VALUES (?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < PRODUCTS; i++) {
                        prod.setString(1, "CheckVol Product " + i);
                        prod.setString(2, "CheckVolCat" + (i % 10));
                        prod.setLong(3, 1000 + (i % 50) * 100);
                        prod.setInt(4, i % 40);                 // some at 0, some low, some plenty
                        prod.setInt(5, 5);
                        prod.addBatch();
                    }
                    prod.executeBatch();
                }
                long firstProduct = scalar(c, "SELECT MIN(product_id) FROM products WHERE product_name LIKE 'CheckVol Product %'");
                try (PreparedStatement ord = c.prepareStatement(
                        "INSERT INTO orders (customer_id, employee_id, order_date, total_amount_cents) VALUES (?, ?, ?, ?)")) {
                    for (int i = 0; i < HEAVY_ORDERS; i++) {
                        int quantity = 1 + (i % 4);
                        long price = 1000 + (i % 50) * 100;
                        int day = i % 28;                                    // spread over January to March, many orders on each day
                        String date = String.format("2026-%02d-%02d 10:%02d:00", 1 + (i / 28) % 3, 1 + day, i % 60);
                        ord.setLong(1, heavy);
                        ord.setLong(2, employeeId);
                        ord.setString(3, date);
                        ord.setLong(4, quantity * price);
                        ord.addBatch();
                    }
                    ord.executeBatch();
                }
                // Ask the database which numbers the new orders got (they are NOT simply "last + 1":
                // SQLite keeps counting past orders that were deleted earlier).
                List<Long> orderIds = new ArrayList<>();
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT order_id FROM orders WHERE customer_id = " + heavy + " ORDER BY order_id")) {
                    while (rs.next()) {
                        orderIds.add(rs.getLong(1));
                    }
                }
                try (PreparedStatement item = c.prepareStatement(
                        "INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents, subtotal_cents) VALUES (?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < HEAVY_ORDERS; i++) {
                        int quantity = 1 + (i % 4);
                        long price = 1000 + (i % 50) * 100;
                        item.setLong(1, orderIds.get(i));
                        item.setLong(2, firstProduct + (i % PRODUCTS));
                        item.setInt(3, quantity);
                        item.setLong(4, price);
                        item.setLong(5, quantity * price);
                        item.addBatch();
                    }
                    item.executeBatch();
                }
                c.commit();
                return heavy;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Removes ONLY the rows this program created (orders first: they point at customers and products). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM orders WHERE customer_id IN (SELECT customer_id FROM customers WHERE first_name LIKE 'CheckVol%')");
            st.executeUpdate("DELETE FROM stock_adjustments WHERE product_id IN (SELECT product_id FROM products WHERE product_name LIKE 'CheckVol %')");
            st.executeUpdate("DELETE FROM products WHERE product_name LIKE 'CheckVol %'");
            st.executeUpdate("DELETE FROM customers WHERE first_name LIKE 'CheckVol%'");
        } catch (SQLException e) {
            System.out.println("WARNING: could not remove all test rows: " + e.getMessage());
        }
    }

    // ---------- helpers ----------

    private static boolean isNewestFirst(List<CustomerOrder> list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i - 1).getOrderDate().compareTo(list.get(i).getOrderDate()) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean reportHasCustomer(ReportResult r, String namePart) {
        return r.getRows().stream().anyMatch(row -> String.valueOf(row[0]).contains(namePart));
    }

    private static <T> T timed(String what, Callable<T> action) throws Exception {
        long t0 = System.currentTimeMillis();
        T result = action.call();
        long ms = System.currentTimeMillis() - t0;
        info(what + ": " + ms + " ms");
        check(what + " is not slow (under " + SLOW_MS + " ms)", ms < SLOW_MS);
        return result;
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            return scalar(c, sql);
        }
    }

    private static long scalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
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
